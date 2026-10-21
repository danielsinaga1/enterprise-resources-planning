package id.enterpriseapps.lims.service;

import id.enterpriseapps.lims.dto.LimsAuditLogDto;
import id.enterpriseapps.lims.entity.LimsAuditLog;
import id.enterpriseapps.lims.entity.LimsUser;
import id.enterpriseapps.lims.repository.LimsAuditLogRepository;
import id.enterpriseapps.lims.repository.LimsUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Penulis jejak audit LIMS.
 *
 * <p>Semua penulisan ikut transaksi pemanggil ({@code limsTransactionManager},
 * database yang sama), jadi jejak audit dan perubahan datanya commit atau
 * rollback bersama — tidak mungkin ada perubahan tanpa jejak, atau jejak atas
 * perubahan yang gagal.
 *
 * <p>Pelaku diambil dari {@link SecurityContextHolder}, bukan dari body request,
 * supaya identitas tidak bisa dipalsukan lewat payload.
 */
@Service
@RequiredArgsConstructor
public class LimsAuditService {

    public static final String ENTITY_LABORATORY_REPORT = "LABORATORY_REPORT";
    public static final String ENTITY_ANALYSIS_DETAIL   = "ANALYSIS_DETAIL";
    public static final String ENTITY_DAILY_REPORT      = "DAILY_REPORT";

    public static final String ACTION_CREATE        = "CREATE";
    public static final String ACTION_UPDATE        = "UPDATE";
    public static final String ACTION_DELETE        = "DELETE";
    public static final String ACTION_STATUS_CHANGE = "STATUS_CHANGE";

    private final LimsAuditLogRepository auditRepository;
    private final LimsUserRepository userRepository;

    /** Pelaku perubahan; {@code id} null bila principal tidak ada di snapshot lims_users. */
    public record Actor(Long id, String name) {
    }

    /**
     * Pelaku dari konteks keamanan. Principal berisi email (login lokal) atau
     * NPK (login LDAP), jadi keduanya dicoba terhadap snapshot lims_users.
     * Tidak pernah melempar: jejak audit yang pelakunya tidak dikenal tetap
     * lebih berguna daripada operasi yang gagal karena pencatatan.
     */
    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public Actor currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String principal = (auth == null) ? null : auth.getName();
        if (principal == null || principal.isBlank()) {
            return new Actor(null, "SYSTEM");
        }
        return userRepository.findFirstByEmailIgnoreCaseOrNpk(principal, principal)
                .map(u -> new Actor(u.getId(), displayName(u, principal)))
                .orElseGet(() -> new Actor(null, principal));
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public void recordCreate(String entityType, Long entityId, String parentType, Long parentId, String summary) {
        write(entityType, entityId, parentType, parentId, ACTION_CREATE, null, null, summary, null, currentActor());
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public void recordDelete(String entityType, Long entityId, String parentType, Long parentId, String summary, String reason) {
        write(entityType, entityId, parentType, parentId, ACTION_DELETE, null, summary, null, reason, currentActor());
    }

    @Transactional(transactionManager = "limsTransactionManager")
    public void recordStatusChange(String entityType, Long entityId, String parentType, Long parentId,
                                   String fromStatus, String toStatus, String reason) {
        write(entityType, entityId, parentType, parentId, ACTION_STATUS_CHANGE,
                "status", fromStatus, toStatus, reason, currentActor());
    }

    /**
     * Mencatat setiap field yang berubah sebagai satu baris jejak.
     *
     * @return jumlah baris jejak yang ditulis (0 bila tidak ada yang berubah)
     */
    @Transactional(transactionManager = "limsTransactionManager")
    public int recordChanges(String entityType, Long entityId, String parentType, Long parentId,
                             LimsAuditChanges changes, String reason) {
        if (changes == null || changes.isEmpty()) {
            return 0;
        }
        Actor actor = currentActor();
        List<LimsAuditLog> rows = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : changes.asMap().entrySet()) {
            rows.add(build(entityType, entityId, parentType, parentId, ACTION_UPDATE,
                    entry.getKey(), entry.getValue()[0], entry.getValue()[1], reason, actor));
        }
        auditRepository.saveAll(rows);
        return rows.size();
    }

    /** Jejak satu laporan beserta detail analisisnya, terbaru di atas. */
    @Transactional(transactionManager = "limsTransactionManager", readOnly = true)
    public List<LimsAuditLogDto> findTrail(String parentType, Long parentId) {
        return auditRepository.findByParentTypeAndParentIdOrderByCreatedAtDesc(parentType, parentId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private void write(String entityType, Long entityId, String parentType, Long parentId, String action,
                       String field, String oldValue, String newValue, String reason, Actor actor) {
        auditRepository.save(build(entityType, entityId, parentType, parentId, action,
                field, oldValue, newValue, reason, actor));
    }

    private LimsAuditLog build(String entityType, Long entityId, String parentType, Long parentId, String action,
                               String field, String oldValue, String newValue, String reason, Actor actor) {
        return LimsAuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .parentType(parentType)
                .parentId(parentId)
                .action(action)
                .fieldName(field)
                .oldValue(oldValue)
                .newValue(newValue)
                .reason(reason)
                .actorId(actor.id())
                .actorName(actor.name())
                .build();
    }

    private LimsAuditLogDto toDto(LimsAuditLog log) {
        return LimsAuditLogDto.builder()
                .id(log.getId())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .action(log.getAction())
                .fieldName(log.getFieldName())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .reason(log.getReason())
                .actorId(log.getActorId())
                .actorName(log.getActorName())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String displayName(LimsUser user, String fallback) {
        return (user.getName() == null || user.getName().isBlank()) ? fallback : user.getName();
    }
}
