package id.enterpriseapps.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import id.enterpriseapps.event.KafkaTopics;
import id.enterpriseapps.event.NotificationRequestEvent;
import id.enterpriseapps.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumes the async notification pipeline: NotificationService publishes a
 * NotificationRequestEvent instead of writing alert rows + pushing FCM inline
 * in the request thread; this listener performs the actual delivery.
 *
 * Delivery failures are logged and the offset is still committed —
 * notifications are best-effort by design (matching the pre-Kafka behavior
 * where FCM/DB errors were swallowed), so one bad message never blocks the
 * pipeline.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class NotificationRequestConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_REQUESTS, groupId = "irms-notification")
    public void onNotificationRequest(NotificationRequestEvent event) {
        if (event == null || event.getTargetUserIds() == null || event.getTargetUserIds().isEmpty()) {
            log.warn("Skipping notification request without targets: {}", event);
            return;
        }
        log.debug("Delivering notification '{}' to {} user(s)", event.getAlertText(),
                event.getTargetUserIds().size());
        notificationService.deliverNotification(event.getAlertText(), event.getAlertLink(),
                event.getAlertType(), event.getReferenceId(), event.getReferenceType(),
                event.getTargetUserIds());
    }
}
