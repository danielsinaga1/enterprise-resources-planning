package id.enterpriseapps.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;

import id.enterpriseapps.entity.FcmTokenEntity;
import id.enterpriseapps.repository.FcmTokenRepository;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FcmService {

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private RetryRegistry retryRegistry;

    // ============================================================
    // TOKEN MANAGEMENT
    // ============================================================

    /**
     * Register or update an FCM token for a user.
     * If the token already exists for a different user, it's reassigned.
     */
    @Transactional
    public void registerToken(Long userId, String token, String deviceInfo) {
        FcmTokenEntity existing = fcmTokenRepository.findByToken(token).orElse(null);

        if (existing != null) {
            // Token exists — update user assignment if changed
            existing.setUserId(userId);
            if (deviceInfo != null) {
                existing.setDeviceInfo(deviceInfo);
            }
            fcmTokenRepository.save(existing);
        } else {
            // New token
            FcmTokenEntity entity = new FcmTokenEntity();
            entity.setUserId(userId);
            entity.setToken(token);
            entity.setDeviceInfo(deviceInfo);
            fcmTokenRepository.save(entity);
        }
    }

    /**
     * Remove a token (e.g. on logout).
     */
    @Transactional
    public void removeToken(String token) {
        fcmTokenRepository.deleteByToken(token);
    }

    // ============================================================
    // PUSH NOTIFICATIONS
    // ============================================================

    /**
     * Send a push notification to a list of user IDs. Runs asynchronously on the dedicated
     * {@code fcmExecutor} pool so it never blocks the request thread, and the actual Firebase
     * call is retried with exponential backoff + jitter on transient failures.
     *
     * <p>Resolves userIds → FCM tokens and sends via Firebase Admin SDK.
     */
    @Async("fcmExecutor")
    public void sendToUsers(List<Long> userIds, String title, String body,
                            java.util.Map<String, String> data) {
        if (userIds == null || userIds.isEmpty()) return;

        List<FcmTokenEntity> tokenEntities = fcmTokenRepository.findByUserIdIn(userIds);
        List<String> tokens = tokenEntities.stream()
                .map(FcmTokenEntity::getToken)
                .collect(Collectors.toList());

        if (tokens.isEmpty()) return;

        sendMulticast(tokens, title, body, data);
    }

    /**
     * Send multicast push notification to a list of FCM tokens. The send itself is wrapped in a
     * Resilience4j retry (instance "fcm"); per-token failures (invalid/expired tokens) are not
     * exceptions — they come back in the BatchResponse and are cleaned up, not retried.
     */
    private void sendMulticast(List<String> tokens, String title, String body,
                               java.util.Map<String, String> data) {
        MulticastMessage.Builder builder = MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .addAllTokens(tokens);

        if (data != null && !data.isEmpty()) {
            builder.putAllData(data);
        }
        MulticastMessage message = builder.build();

        Retry retry = retryRegistry.retry("fcm");
        try {
            BatchResponse response = retry.executeCallable(
                    () -> FirebaseMessaging.getInstance().sendEachForMulticast(message));

            log.info("[FCM] Sent: {} success, {} failed", response.getSuccessCount(), response.getFailureCount());

            // Clean up invalid tokens
            if (response.getFailureCount() > 0) {
                cleanupInvalidTokens(tokens, response);
            }
        } catch (Exception e) {
            // Exhausted retries (Firebase unreachable / persistent error). Log and drop — the
            // in-app notification is already persisted, so the user still sees it in the bell.
            log.error("[FCM] Failed to send multicast after retries: {}", e.getMessage());
        }
    }

    /**
     * Remove tokens that Firebase reported as invalid.
     */
    @Transactional
    private void cleanupInvalidTokens(List<String> tokens, BatchResponse response) {
        var responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            if (!responses.get(i).isSuccessful()) {
                String errorCode = responses.get(i).getException() != null
                        ? responses.get(i).getException().getMessagingErrorCode() != null
                                ? responses.get(i).getException().getMessagingErrorCode().name()
                                : ""
                        : "";
                // Remove unregistered or invalid tokens
                if ("UNREGISTERED".equals(errorCode) || "INVALID_ARGUMENT".equals(errorCode)) {
                    String invalidToken = tokens.get(i);
                    fcmTokenRepository.deleteByToken(invalidToken);
                    log.info("[FCM] Cleaned up invalid token: {}...", invalidToken.substring(0, 20));
                }
            }
        }
    }
}

