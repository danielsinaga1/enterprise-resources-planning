package id.enterpriseapps.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Consumer-side retry/backoff for the Kafka listeners.
 *
 * <p>Defaults matter more than they look. Without an explicit error handler Spring Boot falls
 * back to {@code FixedBackOff(0, 9)}: ten attempts with <b>no delay at all</b>, which a database
 * blip outlives easily — all ten burn in a couple of milliseconds, the record is logged and
 * skipped, and the event is gone for good. The values here exist so a transient failure is
 * actually waited out instead of raced through.
 *
 * <p>Total wait with the defaults is roughly 1+2+4+8 = 15s before the record is handed to the
 * dead-letter topic. Raising {@link #maxRetries} also blocks the partition for longer, so
 * lengthen it only when the downstream really does recover on that timescale.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.kafka.retry")
public class KafkaRetryProperties {

    /** Retries after the first failed attempt (total attempts = this + 1). */
    private int maxRetries = 4;

    /** Delay before the first retry, in milliseconds. */
    private long initialInterval = 1_000L;

    /** Factor applied to the interval after each attempt. */
    private double multiplier = 2.0;

    /** Ceiling for the interval, in milliseconds, however many retries are left. */
    private long maxInterval = 10_000L;
}
