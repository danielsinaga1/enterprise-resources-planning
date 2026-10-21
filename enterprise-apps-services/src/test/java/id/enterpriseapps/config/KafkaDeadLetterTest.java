package id.enterpriseapps.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import id.enterpriseapps.event.DomainEventPublisher;
import id.enterpriseapps.event.IncidentReportEvent;
import id.enterpriseapps.event.KafkaTopics;
import id.enterpriseapps.event.SafetyObservationEvent;
import id.enterpriseapps.property.KafkaRetryProperties;

/**
 * Membuktikan event yang gagal diproses TIDAK hilang diam-diam.
 *
 * <p>Sebelum ada {@code kafkaErrorHandler}, Spring Boot memakai handler bawaan:
 * sepuluh percobaan tanpa jeda sama sekali, lalu record dibuang dan offset tetap
 * di-commit. Gangguan DB dua detik pun cukup untuk menghabiskan sepuluh percobaan
 * itu dalam milidetik — event lenyap tanpa sisa selain satu baris log. Dampak
 * terburuknya di {@code LimsUserEventConsumer}: snapshot {@code lims_users}
 * melenceng dari {@code auth_users} tanpa ada yang tahu.
 *
 * <p>Broker-nya in-JVM ({@code @EmbeddedKafka}), jadi test ini tidak bergantung
 * pada Docker — sama seperti {@code LimsLabReportEventKafkaTest}.
 *
 * <p>Interval retry sengaja dipendekkan lewat properti supaya test selesai dalam
 * ratusan milidetik, bukan 15 detik seperti default produksi.
 */
@SpringBootTest(classes = KafkaDeadLetterTest.TestApp.class, properties = {
        "app.kafka.enabled=true",
        "app.kafka.retry.max-retries=2",
        "app.kafka.retry.initial-interval=50",
        "app.kafka.retry.multiplier=1.0",
        "app.kafka.retry.max-interval=50",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
        "spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=id.enterpriseapps.event"
})
@EmbeddedKafka(partitions = 1, topics = {
        KafkaTopics.INCIDENT_EVENTS,
        KafkaTopics.INCIDENT_EVENTS + KafkaConfig.DLT_SUFFIX,
        KafkaTopics.SAFETY_EVENTS,
        KafkaTopics.SAFETY_EVENTS + KafkaConfig.DLT_SUFFIX
})
class KafkaDeadLetterTest {

    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private FailingConsumer consumer;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private EmbeddedKafkaBroker broker;

    private void waitForAssignment() {
        registry.getListenerContainers()
                .forEach(c -> ContainerTestUtils.waitForAssignment(c, broker.getPartitionsPerTopic()));
    }

    @Test
    void eventYangGagalDiprosesMendaratDiDlt() throws Exception {
        waitForAssignment();

        publisher.send(KafkaTopics.INCIDENT_EVENTS, "7", IncidentReportEvent.builder()
                .reportId(7L)
                .toStatus("Verified")
                .occurredAt(Instant.now())
                .build());

        IncidentReportEvent dead = consumer.incidentDlt.poll(20, TimeUnit.SECONDS);
        assertThat(dead).as("event gagal seharusnya mendarat di topik .DLT, bukan hilang").isNotNull();
        assertThat(dead.getReportId()).isEqualTo(7L);
        assertThat(dead.getToStatus()).isEqualTo("Verified");

        // max-retries=2 berarti 1 percobaan awal + 2 ulangan sebelum menyerah ke DLT.
        assertThat(consumer.incidentAttempts.get())
                .as("backoff harus benar-benar mengulang, bukan langsung menyerah")
                .isEqualTo(3);
    }

    /**
     * Payload cacat tidak akan membaik dengan ditunggu. {@code IllegalArgumentException}
     * terdaftar sebagai non-retryable, jadi record-nya harus langsung ke DLT setelah
     * SATU percobaan — menahan partisi selama siklus backoff untuk itu cuma buang waktu.
     */
    @Test
    void payloadCacatLangsungKeDltTanpaMengulang() throws Exception {
        waitForAssignment();

        publisher.send(KafkaTopics.SAFETY_EVENTS, "9", SafetyObservationEvent.builder()
                .observationId(9L)
                .type("JSO")
                .toStatus("Approved")
                .occurredAt(Instant.now())
                .build());

        SafetyObservationEvent dead = consumer.safetyDlt.poll(20, TimeUnit.SECONDS);
        assertThat(dead).as("payload cacat seharusnya tetap sampai ke DLT").isNotNull();
        assertThat(dead.getObservationId()).isEqualTo(9L);

        assertThat(consumer.safetyAttempts.get())
                .as("exception non-retryable tidak boleh diulang sama sekali")
                .isEqualTo(1);
    }

    @Configuration
    @EnableKafka
    @EnableConfigurationProperties(KafkaRetryProperties.class)
    @Import({ KafkaAutoConfiguration.class, DomainEventPublisher.class, KafkaConfig.class })
    static class TestApp {

        @Bean
        FailingConsumer failingConsumer() {
            return new FailingConsumer();
        }
    }

    /**
     * Dua listener yang selalu gagal, plus dua listener yang mengintip topik .DLT-nya.
     */
    static class FailingConsumer {

        final AtomicInteger incidentAttempts = new AtomicInteger();
        final AtomicInteger safetyAttempts = new AtomicInteger();
        final BlockingQueue<IncidentReportEvent> incidentDlt = new LinkedBlockingQueue<>();
        final BlockingQueue<SafetyObservationEvent> safetyDlt = new LinkedBlockingQueue<>();

        @KafkaListener(topics = KafkaTopics.INCIDENT_EVENTS, groupId = "dlt-test-incident")
        void onIncident(IncidentReportEvent event) {
            incidentAttempts.incrementAndGet();
            throw new IllegalStateException("kegagalan sementara yang disengaja");
        }

        @KafkaListener(topics = KafkaTopics.INCIDENT_EVENTS + KafkaConfig.DLT_SUFFIX,
                groupId = "dlt-test-incident-dlt")
        void onIncidentDlt(IncidentReportEvent event) {
            incidentDlt.add(event);
        }

        @KafkaListener(topics = KafkaTopics.SAFETY_EVENTS, groupId = "dlt-test-safety")
        void onSafety(SafetyObservationEvent event) {
            safetyAttempts.incrementAndGet();
            throw new IllegalArgumentException("payload dianggap cacat");
        }

        @KafkaListener(topics = KafkaTopics.SAFETY_EVENTS + KafkaConfig.DLT_SUFFIX,
                groupId = "dlt-test-safety-dlt")
        void onSafetyDlt(SafetyObservationEvent event) {
            safetyDlt.add(event);
        }
    }
}
