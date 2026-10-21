package id.enterpriseapps.event;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published on {@link KafkaTopics#LIMS_LABREPORT_EVENTS} for every laboratory
 * report workflow transition (Open → Assigned → Reviewed → Close, plus
 * rejection).
 *
 * Carries {@code noReport} alongside the id because LIMS owns its own database
 * (db_lims): a downstream consumer cannot join back to the report row, so the
 * human-readable number has to travel with the event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimsLabReportEvent {

    private Long reportId;
    /** Human-readable report number (LimsLaboratoryReport.noReport). */
    private String noReport;
    /** Workflow status the report transitioned TO ("Open", "Assigned", "Reviewed", "Close", ...). */
    private String toStatus;
    private Instant occurredAt;
}
