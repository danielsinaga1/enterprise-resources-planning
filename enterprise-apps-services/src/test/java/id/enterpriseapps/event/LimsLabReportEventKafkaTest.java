package id.enterpriseapps.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
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

/**
 * Membuktikan event backbone benar-benar mengalir end-to-end: publish lewat
 * DomainEventPublisher → broker → consumer menerima objek bertipe utuh.
 *
 * Memakai broker in-JVM (@EmbeddedKafka), bukan Testcontainers, supaya tidak
 * bergantung pada Docker sama sekali — docker-compose lokal masih menunjuk
 * MySQL sehingga tidak bisa dipakai memverifikasi apa pun saat ini.
 *
 * Properti spring.kafka.* di bawah sengaja menyalin application.yaml: yang
 * diuji bukan cuma "pesan sampai", tapi bahwa kontrak serialisasi JSON
 * (__TypeId__ + trusted packages) memetakan payload kembali ke kelas event.
 * Konteks Spring-nya sengaja minimal — hanya autoconfig Kafka — supaya test
 * tidak butuh satu pun dari empat DataSource aplikasi.
 */
@SpringBootTest(classes = LimsLabReportEventKafkaTest.TestApp.class, properties = {
        "app.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.consumer.group-id=lims-labreport-test",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
        "spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=id.enterpriseapps.event"
})
@EmbeddedKafka(partitions = 1, topics = KafkaTopics.LIMS_LABREPORT_EVENTS)
class LimsLabReportEventKafkaTest {

    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private Recorder recorder;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void transisiLabReportSampaiKeConsumerDenganTipeUtuh() throws Exception {
        // Tanpa menunggu partisi ter-assign, pesan bisa terkirim sebelum
        // consumer siap dan test jadi flaky.
        registry.getListenerContainers()
                .forEach(c -> ContainerTestUtils.waitForAssignment(c, broker.getPartitionsPerTopic()));

        boolean handedToProducer = publisher.send(
                KafkaTopics.LIMS_LABREPORT_EVENTS,
                "42",
                LimsLabReportEvent.builder()
                        .reportId(42L)
                        .noReport("LAB-2026-0042")
                        .toStatus("Reviewed")
                        .occurredAt(Instant.now())
                        .build());

        assertThat(handedToProducer).isTrue();

        LimsLabReportEvent received = recorder.received.poll(20, TimeUnit.SECONDS);
        assertThat(received).as("event tidak sampai ke consumer").isNotNull();
        assertThat(received.getReportId()).isEqualTo(42L);
        assertThat(received.getNoReport()).isEqualTo("LAB-2026-0042");
        assertThat(received.getToStatus()).isEqualTo("Reviewed");
        assertThat(received.getOccurredAt()).isNotNull();
    }

    /**
     * Jaminan "aplikasi tetap jalan tanpa broker": saat app.kafka.enabled=false
     * publisher tidak boleh menyentuh KafkaTemplate sama sekali — makanya
     * ObjectProvider-nya sengaja null di sini. Kalau suatu saat urutan
     * pengecekan di send() terbalik, test ini gagal dengan NPE.
     */
    @Test
    void tidakMengirimApaPunSaatKafkaDimatikan() {
        DomainEventPublisher off = new DomainEventPublisher(false, null);
        assertThat(off.isEnabled()).isFalse();
        assertThat(off.send(KafkaTopics.LIMS_LABREPORT_EVENTS, "1", "payload")).isFalse();
    }

    @Configuration
    @EnableKafka
    @Import({ KafkaAutoConfiguration.class, DomainEventPublisher.class })
    static class TestApp {

        @Bean
        Recorder recorder() {
            return new Recorder();
        }
    }

    static class Recorder {

        final BlockingQueue<LimsLabReportEvent> received = new LinkedBlockingQueue<>();

        @KafkaListener(topics = KafkaTopics.LIMS_LABREPORT_EVENTS, groupId = "lims-labreport-test")
        void on(LimsLabReportEvent event) {
            received.add(event);
        }
    }
}
