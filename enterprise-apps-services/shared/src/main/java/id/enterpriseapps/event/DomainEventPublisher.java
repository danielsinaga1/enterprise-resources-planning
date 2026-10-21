package id.enterpriseapps.event;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Generic, best-effort Kafka producer for the whole platform — the one place
 * that actually talks to a broker.
 *
 * It lives in the shared kernel because feature modules must be able to
 * publish without importing each other: LIMS may not depend on
 * {@code id.enterpriseapps.service..} (see ModuleBoundaryTest), so it cannot
 * reuse the core EventPublisher. The core's EventPublisher now delegates here,
 * keeping the send/swallow logic in a single implementation.
 *
 * Publishing is strictly asynchronous and never fatal: domain events are a
 * side channel (metrics, snapshots, notifications), so a down broker must
 * never fail or slow a business transaction. When
 * {@code app.kafka.enabled=false} every publish is a no-op, which is why the
 * KafkaTemplate is resolved lazily via ObjectProvider — the app boots and runs
 * with no broker configured at all.
 */
@Component
@Slf4j
public class DomainEventPublisher {

    private final boolean enabled;
    private final ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplate;

    public DomainEventPublisher(@Value("${app.kafka.enabled:false}") boolean enabled,
                                ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplate) {
        this.enabled = enabled;
        this.kafkaTemplate = kafkaTemplate;
    }

    /** True when Kafka publishing is switched on (app.kafka.enabled). */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param key partition key, or null to round-robin
     * @return true if the event was handed to the producer, false if Kafka is
     *         off or unavailable and the caller must fall back to a synchronous path
     */
    public boolean send(String topic, String key, Object payload) {
        if (!enabled) return false;
        KafkaTemplate<String, Object> template = kafkaTemplate.getIfAvailable();
        if (template == null) {
            log.warn("Kafka enabled but no KafkaTemplate available — dropping event for topic {}", topic);
            return false;
        }
        try {
            template.send(topic, key, payload).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Kafka publish to {} failed (non-blocking): {}", topic, ex.getMessage());
                }
            });
            return true;
        } catch (Exception e) {
            log.warn("Kafka publish to {} failed (non-blocking): {}", topic, e.getMessage());
            return false;
        }
    }
}
