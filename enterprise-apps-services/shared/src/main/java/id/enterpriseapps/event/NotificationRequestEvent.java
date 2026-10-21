package id.enterpriseapps.event;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published on {@link KafkaTopics#NOTIFICATION_REQUESTS} instead of writing
 * alerts + pushing FCM inline in the request thread. The core app consumes
 * this topic and performs the actual delivery (DB alert rows + FCM push),
 * making notification sending async and replayable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequestEvent {

    private String alertText;
    private String alertLink;
    private String alertType;
    private Long referenceId;
    private String referenceType;
    private List<Long> targetUserIds;
    private Instant occurredAt;
}
