package id.enterpriseapps.event;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published on {@link KafkaTopics#SAFETY_EVENTS} for every safety observation
 * workflow transition (open/reviewed/in_progress/action_taken/closed/...).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyObservationEvent {

    private Long observationId;
    /** Observation type, e.g. "NCR" or "JSO". */
    private String type;
    /** Workflow status the observation transitioned TO (e.g. "open", "reviewed", "closed"). */
    private String toStatus;
    private Instant occurredAt;
}
