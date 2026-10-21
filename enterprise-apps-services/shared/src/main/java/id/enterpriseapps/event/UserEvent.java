package id.enterpriseapps.event;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published on {@link KafkaTopics#USER_EVENTS} whenever an auth user is
 * created, updated, deleted or has its status toggled. Carries the full
 * snapshot payload so consumers can upsert their local read-side copy
 * (e.g. lims_users) without calling back into the auth database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent {

    public enum Action { CREATED, UPDATED, DELETED, STATUS_CHANGED }

    private Action action;
    private Long id;
    private String npk;
    private String npp;
    private String name;
    private String email;
    private String mobilePhone;
    private String rfId;
    private Long parentId;
    private Long jobId;
    private Long deptId;
    private Long divisionId;
    private Short status;
    private Instant occurredAt;
}
