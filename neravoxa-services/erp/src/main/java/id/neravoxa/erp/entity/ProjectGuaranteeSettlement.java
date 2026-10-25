package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel project_guarantee_settlements. (Dibangkitkan dari model Prisma ProjectGuaranteeSettlement.) */
@Entity
@Table(name = "project_guarantee_settlements")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class ProjectGuaranteeSettlement {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "guarantee_id", nullable = false)
    private String guaranteeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guarantee_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"settlements", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private ProjectGuarantee guarantee;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Column(name = "return_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal returnAmount = BigDecimal.ZERO;

    @Column(name = "forfeit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal forfeitAmount = BigDecimal.ZERO;

    @Column(name = "return_account_id")
    private String returnAccountId;

    @Column(name = "forfeit_account_id")
    private String forfeitAccountId;

    @Column(name = "journal_entry_id")
    private String journalEntryId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "is_cancelled", nullable = false)
    private Boolean isCancelled = false;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
