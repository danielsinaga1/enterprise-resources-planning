package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel bank_reconciliations. (Dibangkitkan dari model Prisma BankReconciliation.) */
@Entity
@Table(name = "bank_reconciliations")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class BankReconciliation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"reconciliations", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account account;

    @Column(name = "statement_date", nullable = false)
    private Instant statementDate;

    @Column(name = "statement_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal statementBalance;

    @Column(name = "book_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal bookBalance = BigDecimal.ZERO;

    @Column(name = "status", nullable = false)
    private String status = "Draft";

    @Column(name = "notes")
    private String notes;

    @OneToMany(mappedBy = "reconciliation", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"reconciliation"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<BankReconciliationItem> items;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onPreUpdate() {
        updatedAt = Instant.now();
    }

}
