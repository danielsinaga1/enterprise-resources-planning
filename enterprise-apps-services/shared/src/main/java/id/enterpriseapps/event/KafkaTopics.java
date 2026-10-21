package id.enterpriseapps.event;

/**
 * Central registry of Kafka topic names. Lives in the shared kernel so both
 * the core app (producers) and feature modules like LIMS (consumers) agree on
 * the same names without depending on each other.
 *
 * Naming: {owning-domain}.{entity}.{kind}. All topics are created by
 * KafkaConfig in the bootable app when app.kafka.enabled=true.
 */
public final class KafkaTopics {

    /** Auth user lifecycle (created/updated/deleted/status) — feeds read-side snapshots (irms_users, lims_users). */
    public static final String USER_EVENTS = "auth.user.events";

    /** Incident report workflow transitions (created + every status change). */
    public static final String INCIDENT_EVENTS = "irms.incident.events";

    /** Safety observation workflow transitions. */
    public static final String SAFETY_EVENTS = "irms.safety.events";

    /** Laboratory report workflow transitions (Open/Assigned/Reviewed/Close/Rejected). */
    public static final String LIMS_LABREPORT_EVENTS = "lims.labreport.events";

    /** Async notification pipeline — consumed by the core app to persist alerts + push FCM. */
    public static final String NOTIFICATION_REQUESTS = "irms.notification.requests";

    private KafkaTopics() {
    }
}
