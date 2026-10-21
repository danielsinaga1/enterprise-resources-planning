package id.enterpriseapps.lims.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.enterpriseapps.event.UserEvent;
import id.enterpriseapps.lims.entity.LimsUser;
import id.enterpriseapps.lims.repository.LimsUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the local {@code lims_users} snapshot in sync with auth users via
 * {@code auth.user.events}. Before this consumer existed the snapshot was
 * seeded once by SQL and silently went stale.
 *
 * Same philosophy as the core's IrmsUserSyncService: auth is the source of
 * truth, the snapshot is secondary — errors are logged, never rethrown, so a
 * bad record can't wedge the Kafka listener.
 *
 * Note: {@code job_title} is deliberately left untouched — the event carries
 * the auth job ID, not the display title this table stores.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LimsUserSyncService {

    private final LimsUserRepository limsUserRepository;

    @Transactional(transactionManager = "limsTransactionManager")
    public void apply(UserEvent event) {
        if (event == null || event.getId() == null || event.getAction() == null) {
            log.warn("lims_users sync: ignoring malformed user event {}", event);
            return;
        }
        try {
            if (event.getAction() == UserEvent.Action.DELETED) {
                limsUserRepository.deleteById(event.getId());
                log.info("lims_users: deleted snapshot for user id={}", event.getId());
                return;
            }
            LimsUser user = limsUserRepository.findById(event.getId()).orElseGet(LimsUser::new);
            user.setId(event.getId());
            user.setName(event.getName());
            user.setNpk(event.getNpk());
            user.setEmail(event.getEmail());
            user.setParentId(event.getParentId());
            user.setDepartmentId(event.getDeptId());
            limsUserRepository.save(user);
            log.info("lims_users: upserted snapshot for user id={} ({})", event.getId(), event.getAction());
        } catch (Exception e) {
            log.warn("lims_users sync failed for user id={}: {}", event.getId(), e.getMessage());
        }
    }
}
