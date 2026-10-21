package id.enterpriseapps.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import id.enterpriseapps.event.KafkaTopics;
import id.enterpriseapps.property.KafkaRetryProperties;

/**
 * Kafka event backbone. Everything here (and every @KafkaListener in the app)
 * is gated behind {@code app.kafka.enabled} so the application still boots and
 * works normally on machines without a broker — producers no-op and
 * notifications fall back to synchronous delivery (see EventPublisher /
 * NotificationService).
 *
 * Serialization is JSON (spring.kafka.* in application.yaml): the producer
 * adds __TypeId__ headers and consumers map them back to the shared event
 * classes in {@code id.enterpriseapps.event}.
 *
 * Topics are declared here and created idempotently by KafkaAdmin at startup.
 * Single-partition / RF=1 matches the single-node dev broker; scale these in
 * the broker or here when a real cluster exists.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    /** Suffix for the dead-letter twin of every topic. Matches Spring's own convention. */
    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    public KafkaAdmin.NewTopics irmsTopics() {
        return new KafkaAdmin.NewTopics(
                topic(KafkaTopics.USER_EVENTS),
                topic(KafkaTopics.INCIDENT_EVENTS),
                topic(KafkaTopics.SAFETY_EVENTS),
                topic(KafkaTopics.LIMS_LABREPORT_EVENTS),
                topic(KafkaTopics.NOTIFICATION_REQUESTS));
    }

    /**
     * Dead-letter twins. Declared up front so the recoverer never has to publish to a topic
     * that does not exist yet — auto-creation is off on real brokers, and a failed recovery
     * is the one failure that loses the message for good.
     */
    @Bean
    public KafkaAdmin.NewTopics irmsDeadLetterTopics() {
        return new KafkaAdmin.NewTopics(
                topic(KafkaTopics.USER_EVENTS + DLT_SUFFIX),
                topic(KafkaTopics.INCIDENT_EVENTS + DLT_SUFFIX),
                topic(KafkaTopics.SAFETY_EVENTS + DLT_SUFFIX),
                topic(KafkaTopics.LIMS_LABREPORT_EVENTS + DLT_SUFFIX),
                topic(KafkaTopics.NOTIFICATION_REQUESTS + DLT_SUFFIX));
    }

    /**
     * What happens when a listener throws.
     *
     * <p>Replaces Boot's default handler, which retries ten times with <b>zero</b> delay and
     * then drops the record. That default quietly turns a two-second database hiccup into
     * permanent data loss: the ten attempts finish in milliseconds, the offset is committed,
     * and nothing is left to show a user event never reached the {@code lims_users} snapshot.
     *
     * <p>Here a failure is instead retried with growing delays and, if it still fails, published
     * to {@code <topic>.DLT} with the original headers plus Spring's diagnostic ones
     * (exception class, message, stack trace, source partition/offset). The pipeline keeps
     * moving either way — nothing blocks a business transaction — but a lost event is now a
     * record sitting in a topic rather than a line in yesterday's log.
     *
     * <p>Deserialization failures are <i>not</i> retried: Spring already classes them as fatal,
     * and a poison pill is just as unparseable on the fifth attempt. They go straight to the DLT.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template,
                                                 KafkaRetryProperties properties) {
        // Partition -1 lets the broker choose, so a DLT may have a different partition count
        // than its source topic without the publish failing.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));

        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(properties.getMaxRetries());
        backOff.setInitialInterval(properties.getInitialInterval());
        backOff.setMultiplier(properties.getMultiplier());
        backOff.setMaxInterval(properties.getMaxInterval());

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // A malformed payload will not become well-formed by waiting; fail it to the DLT at once.
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(1).replicas(1).build();
    }
}
