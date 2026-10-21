package id.enterpriseapps.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import id.enterpriseapps.constant.ErrorCodeConstant;
import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.entity.UserAlertEntity;
import id.enterpriseapps.entity.UserUserAlertEntity;
import id.enterpriseapps.event.NotificationRequestEvent;
import id.enterpriseapps.repository.UserAlertRepository;
import id.enterpriseapps.repository.UserUserAlertRepository;
import id.enterpriseapps.response.Response;

@Service
public class NotificationService {

    @Autowired
    private UserAlertRepository userAlertRepository;

    @Autowired
    private UserUserAlertRepository userUserAlertRepository;

    @Autowired
    private FcmService fcmService;

    @Autowired
    private EventPublisher eventPublisher;

    /**
     * Get notifications for a specific user (paginated)
     */
    public Response getNotifications(Long userId, int page, int limit) {
        try {
            List<UserUserAlertEntity> userAlerts = userUserAlertRepository.findByUserIdOrderByIdDesc(userId);

            int total = userAlerts.size();
            int start = (page - 1) * limit;
            int end = Math.min(start + limit, total);

            if (start >= total) {
                return Response.builder()
                        .status(StatusConstant.SUCCESS)
                        .httpStatus(HttpStatus.OK)
                        .data(new ArrayList<>())
                        .countData(0L)
                        .page(page)
                        .totalPage(0)
                        .build();
            }

            List<UserUserAlertEntity> pagedAlerts = userAlerts.subList(start, end);
            List<Long> alertIds = pagedAlerts.stream()
                    .map(UserUserAlertEntity::getUserAlertId)
                    .collect(Collectors.toList());

            List<UserAlertEntity> alerts = userAlertRepository.findByIdInOrderByCreatedAtDesc(alertIds);
            Map<Long, UserAlertEntity> alertMap = alerts.stream()
                    .collect(Collectors.toMap(UserAlertEntity::getId, a -> a));

            List<Map<String, Object>> result = new ArrayList<>();
            for (UserUserAlertEntity ua : pagedAlerts) {
                UserAlertEntity alert = alertMap.get(ua.getUserAlertId());
                if (alert == null) continue;

                Map<String, Object> item = new HashMap<>();
                item.put("id", alert.getId());
                item.put("alertText", alert.getAlertText());
                item.put("alertLink", alert.getAlertLink());
                item.put("alertType", alert.getAlertType());
                item.put("referenceId", alert.getReferenceId());
                item.put("referenceType", alert.getReferenceType());
                item.put("isRead", ua.getIsRead());
                item.put("createdAt", alert.getCreatedAt());
                result.add(item);
            }

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(result)
                    .countData((long) total)
                    .page(page)
                    .totalPage((int) Math.ceil((double) total / limit))
                    .build();
        } catch (Exception e) {
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error)
                    .errorMessage(ErrorCodeConstant.error_message_general)
                    .build();
        }
    }

    /**
     * Get unread count for a user
     */
    public Response getUnreadCount(Long userId) {
        try {
            long count = userUserAlertRepository.countByUserIdAndIsRead(userId, false);
            Map<String, Object> data = new HashMap<>();
            data.put("unreadCount", count);
            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .data(data)
                    .build();
        } catch (Exception e) {
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error)
                    .errorMessage(ErrorCodeConstant.error_message_general)
                    .build();
        }
    }

    /**
     * Mark a single notification as read
     */
    public Response markAsRead(Long alertId, Long userId) {
        try {
            UserUserAlertEntity ua = userUserAlertRepository
                    .findByUserAlertIdAndUserId(alertId, userId)
                    .orElse(null);
            if (ua == null) {
                return Response.builder()
                        .status(StatusConstant.FAILED)
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .errorMessage("Notification not found")
                        .build();
            }
            ua.setIsRead(true);
            userUserAlertRepository.save(ua);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .message("Notification marked as read")
                    .build();
        } catch (Exception e) {
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error)
                    .errorMessage(ErrorCodeConstant.error_message_general)
                    .build();
        }
    }

    /**
     * Mark all notifications as read for a user
     */
    public Response markAllAsRead(Long userId) {
        try {
            List<UserUserAlertEntity> unread = userUserAlertRepository
                    .findByUserIdAndIsRead(userId, false);
            for (UserUserAlertEntity ua : unread) {
                ua.setIsRead(true);
            }
            userUserAlertRepository.saveAll(unread);

            return Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .message("All notifications marked as read")
                    .build();
        } catch (Exception e) {
            return Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorCode(ErrorCodeConstant.General_Error)
                    .errorMessage(ErrorCodeConstant.error_message_general)
                    .build();
        }
    }

    /**
     * Create a notification and assign it to specific users.
     *
     * With Kafka enabled this only publishes a NotificationRequestEvent and
     * returns immediately — NotificationRequestConsumer picks it up and calls
     * {@link #deliverNotification}. Without Kafka it delivers synchronously,
     * preserving the original inline behavior.
     */
    public void createNotification(String alertText, String alertLink, String alertType,
                                   Long referenceId, String referenceType, List<Long> targetUserIds) {
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .alertText(alertText)
                .alertLink(alertLink)
                .alertType(alertType)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .targetUserIds(targetUserIds)
                .build();
        if (!eventPublisher.publishNotificationRequest(event)) {
            deliverNotification(alertText, alertLink, alertType, referenceId, referenceType, targetUserIds);
        }
    }

    /**
     * Actually persist the alert rows and push FCM. Called directly when Kafka
     * is disabled, or from NotificationRequestConsumer when it is enabled.
     */
    public void deliverNotification(String alertText, String alertLink, String alertType,
                                    Long referenceId, String referenceType, List<Long> targetUserIds) {
        try {
            UserAlertEntity alert = new UserAlertEntity();
            alert.setAlertText(alertText);
            alert.setAlertLink(alertLink);
            alert.setAlertType(alertType);
            alert.setReferenceId(referenceId);
            alert.setReferenceType(referenceType);
            alert.setCreatedAt(LocalDateTime.now());
            alert.setUpdatedAt(LocalDateTime.now());
            userAlertRepository.save(alert);

            for (Long userId : targetUserIds) {
                UserUserAlertEntity ua = new UserUserAlertEntity();
                ua.setUserAlertId(alert.getId());
                ua.setUserId(userId);
                ua.setIsRead(false);
                userUserAlertRepository.save(ua);
            }

            // Send FCM push notification to all target users' devices
            try {
                Map<String, String> fcmData = new HashMap<>();
                if (alertLink != null) fcmData.put("alertLink", alertLink);
                if (alertType != null) fcmData.put("alertType", alertType);
                if (referenceId != null) fcmData.put("referenceId", referenceId.toString());
                if (referenceType != null) fcmData.put("referenceType", referenceType);

                fcmService.sendToUsers(targetUserIds, "IRMS", alertText, fcmData);
            } catch (Exception fcmEx) {
                System.err.println("FCM push failed (non-blocking): " + fcmEx.getMessage());
            }
        } catch (Exception e) {
            // Log but don't fail the main operation
            System.err.println("Failed to create notification: " + e.getMessage());
        }
    }
}

