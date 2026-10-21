package id.enterpriseapps.event;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published on {@link KafkaTopics#INCIDENT_EVENTS} for every incident report
 * workflow transition (including creation, which lands in status "New").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentReportEvent {

    private Long reportId;
    /** Workflow status the report transitioned TO (e.g. "New", "Fully Approved", "Done"). */
    private String toStatus;
    private Instant occurredAt;
}
