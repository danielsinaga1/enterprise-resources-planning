package id.enterpriseapps.lims.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Satu baris jejak audit: satu perubahan pada satu field, atau satu transisi
 * status. Tabelnya append-only — ada trigger di database yang menolak UPDATE
 * dan DELETE, jadi entitas ini hanya pernah di-insert.
 *
 * <p>Sengaja TIDAK memperluas {@link LimsBaseEntity}: jejak audit tidak punya
 * soft delete dan tidak pernah diperbarui. Juga tidak ada foreign key ke tabel
 * sumber — jejak harus tetap terbaca walau baris sumbernya sudah dihapus.
 */
@Entity
@Table(name = "lims_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LimsAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LABORATORY_REPORT | ANALYSIS_DETAIL | DAILY_REPORT | ... */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** Induk untuk pengelompokan tampilan, mis. detail dikelompokkan ke laporannya. */
    @Column(name = "parent_type", length = 50)
    private String parentType;

    @Column(name = "parent_id")
    private Long parentId;

    /** CREATE | UPDATE | DELETE | STATUS_CHANGE */
    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "field_name", length = 100)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /** Alasan perubahan — wajib untuk penolakan dan koreksi hasil. */
    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name", length = 255)
    private String actorName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
