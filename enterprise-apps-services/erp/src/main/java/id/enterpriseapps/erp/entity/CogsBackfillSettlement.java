package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel cogs_backfill_settlements. (Dibangkitkan dari model Prisma CogsBackfillSettlement.) */
@Entity
@Table(name = "cogs_backfill_settlements")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class CogsBackfillSettlement {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "queue_id", nullable = false)
    private String queueId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"settlements", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private CogsBackfillQueue queue;

    @Column(name = "qty_settled", nullable = false, precision = 15, scale = 3)
    private BigDecimal qtySettled;

    @Column(name = "cost_at_settle", nullable = false, precision = 20, scale = 2)
    private BigDecimal costAtSettle;

    @Column(name = "differential", nullable = false, precision = 20, scale = 2)
    private BigDecimal differential;

    @Column(name = "journal_entry_id")
    private String journalEntryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"cogsBackfillSettlements", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private JournalEntry journalEntry;

    @Column(name = "trigger_source", nullable = false)
    private String triggerSource;

    @Column(name = "trigger_ref_id")
    private String triggerRefId;

    @Column(name = "trigger_ref_no")
    private String triggerRefNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
