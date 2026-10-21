package id.enterpriseapps.erp.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Pola keterangan ternormalisasi -> akun, dipelajari dari koreksi manusia saat posting. (Dibangkitkan dari model Prisma CashBookLearnedMap.) */
@Entity
@Table(name = "cash_book_learned_maps")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class CashBookLearnedMap {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "pattern", nullable = false)
    private String pattern;

    @Column(name = "txn_type", nullable = false)
    private String txnType;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "hits", nullable = false)
    private Integer hits = 1;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onPreUpdate() {
        updatedAt = Instant.now();
    }

}
