package id.enterpriseapps.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import id.enterpriseapps.event.DomainEventPublisher;
import id.enterpriseapps.event.IncidentReportEvent;
import id.enterpriseapps.event.KafkaTopics;
import id.enterpriseapps.event.NotificationRequestEvent;
import id.enterpriseapps.event.SafetyObservationEvent;
import id.enterpriseapps.event.UserEvent;
import lombok.RequiredArgsConstructor;

/**
 * Typed facade over the core domain's outbound events.
 *
 * The actual talking-to-a-broker lives in the shared
 * {@link DomainEventPublisher} (best-effort, never fatal, no-op when
 * app.kafka.enabled=false); this class only knows which topic and partition
 * key each core event belongs to. LIMS has no equivalent facade — it may not
 * import this package, so it calls DomainEventPublisher directly.
 */
@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final DomainEventPublisher publisher;

    /** True when Kafka publishing is switched on (app.kafka.enabled). */
    public boolean isEnabled() {
        return publisher.isEnabled();
    }

    public void publishUserEvent(UserEvent event) {
        if (event.getOccurredAt() == null) event.setOccurredAt(Instant.now());
        publisher.send(KafkaTopics.USER_EVENTS, String.valueOf(event.getId()), event);
    }

    public void publishIncidentTransition(Long reportId, String toStatus) {
        IncidentReportEvent event = IncidentReportEvent.builder()
                .reportId(reportId)
                .toStatus(toStatus)
                .occurredAt(Instant.now())
                .build();
        publisher.send(KafkaTopics.INCIDENT_EVENTS, String.valueOf(reportId), event);
    }

    public void publishSafetyTransition(Long observationId, String type, String toStatus) {
        SafetyObservationEvent event = SafetyObservationEvent.builder()
                .observationId(observationId)
                .type(type)
                .toStatus(toStatus)
                .occurredAt(Instant.now())
                .build();
        publisher.send(KafkaTopics.SAFETY_EVENTS, String.valueOf(observationId), event);
    }

    /**
     * @return true if the event was handed to the producer (Kafka enabled),
     *         false if Kafka is off and the caller must deliver synchronously.
     */
    public boolean publishNotificationRequest(NotificationRequestEvent event) {
        if (!publisher.isEnabled()) return false;
        if (event.getOccurredAt() == null) event.setOccurredAt(Instant.now());
        return publisher.send(KafkaTopics.NOTIFICATION_REQUESTS, null, event);
    }
}
