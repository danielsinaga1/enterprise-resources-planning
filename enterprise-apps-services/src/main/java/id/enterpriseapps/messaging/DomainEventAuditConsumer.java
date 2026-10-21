package id.enterpriseapps.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import id.enterpriseapps.event.IncidentReportEvent;
import id.enterpriseapps.event.KafkaTopics;
import id.enterpriseapps.event.LimsLabReportEvent;
import id.enterpriseapps.event.SafetyObservationEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * Lightweight audit trail of the domain event stream: logs every incident,
 * safety and laboratory-report workflow transition flowing through Kafka.
 * Doubles as a liveness check for the event backbone (if these lines stop
 * appearing while the app is busy, the pipeline is broken) and as the
 * reference example for future consumers (dashboards, SLA monitors,
 * external integrations).
 *
 * Uses its own consumer group so it reads independently of any other
 * consumer of these topics.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@Slf4j
public class DomainEventAuditConsumer {

    @KafkaListener(topics = KafkaTopics.INCIDENT_EVENTS, groupId = "irms-event-audit")
    public void onIncidentEvent(IncidentReportEvent event) {
        log.info("[event-audit] incident report {} -> '{}' at {}",
                event.getReportId(), event.getToStatus(), event.getOccurredAt());
    }

    @KafkaListener(topics = KafkaTopics.SAFETY_EVENTS, groupId = "irms-event-audit")
    public void onSafetyEvent(SafetyObservationEvent event) {
        log.info("[event-audit] safety observation {} ({}) -> '{}' at {}",
                event.getObservationId(), event.getType(), event.getToStatus(), event.getOccurredAt());
    }

    @KafkaListener(topics = KafkaTopics.LIMS_LABREPORT_EVENTS, groupId = "irms-event-audit")
    public void onLimsLabReportEvent(LimsLabReportEvent event) {
        log.info("[event-audit] lab report {} ({}) -> '{}' at {}",
                event.getReportId(), event.getNoReport(), event.getToStatus(), event.getOccurredAt());
    }
}
