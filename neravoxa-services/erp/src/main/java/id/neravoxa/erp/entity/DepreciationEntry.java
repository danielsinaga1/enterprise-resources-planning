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

/** Tabel depreciation_entries. (Dibangkitkan dari model Prisma DepreciationEntry.) */
@Entity
@Table(name = "depreciation_entries")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class DepreciationEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "fixed_asset_id", nullable = false)
    private String fixedAssetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fixed_asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"depreciationEntries", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private FixedAsset fixedAsset;

    @Column(name = "period_date", nullable = false)
    private Instant periodDate;

    @Column(name = "amount", nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(name = "journal_entry_id")
    private String journalEntryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"depreciationEntries", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private JournalEntry journalEntry;

    @Column(name = "is_posted", nullable = false)
    private Boolean isPosted = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
