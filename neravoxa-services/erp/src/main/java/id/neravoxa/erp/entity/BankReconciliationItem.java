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

/** Tabel bank_reconciliation_items. (Dibangkitkan dari model Prisma BankReconciliationItem.) */
@Entity
@Table(name = "bank_reconciliation_items")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class BankReconciliationItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "reconciliation_id", nullable = false)
    private String reconciliationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"items", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private BankReconciliation reconciliation;

    @Column(name = "ledger_entry_id")
    private String ledgerEntryId;

    @Column(name = "is_matched", nullable = false)
    private Boolean isMatched = false;

    @Column(name = "statement_amount", precision = 15, scale = 2)
    private BigDecimal statementAmount;

    @Column(name = "statement_desc")
    private String statementDesc;

    @Column(name = "statement_date")
    private Instant statementDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
