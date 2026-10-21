package id.enterpriseapps.lims.consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import id.enterpriseapps.event.KafkaTopics;
import id.enterpriseapps.event.UserEvent;
import id.enterpriseapps.lims.service.LimsUserSyncService;
import lombok.RequiredArgsConstructor;

/**
 * LIMS's ear on the auth user stream. Runs in its own consumer group so it
 * tracks offsets independently — when LIMS is extracted into a standalone
 * microservice, this consumer moves with it unchanged and simply reconnects
 * to the same topic from its own deployment.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class LimsUserEventConsumer {

    private final LimsUserSyncService limsUserSyncService;

    @KafkaListener(topics = KafkaTopics.USER_EVENTS, groupId = "lims-user-sync")
    public void onUserEvent(UserEvent event) {
        limsUserSyncService.apply(event);
    }
}
