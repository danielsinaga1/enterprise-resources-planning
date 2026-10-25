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

/** Tabel payments. (Dibangkitkan dari model Prisma Payment.) */
@Entity
@Table(name = "payments")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "payment_number", nullable = false)
    private String paymentNumber;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Column(name = "party_id", nullable = false)
    private String partyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"payments", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Party party;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"payments", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account account;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "refunded_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DocumentStatus status = DocumentStatus.Draft;

    @Column(name = "reference_number")
    private String referenceNo;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"payments", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User user;

    @OneToMany(mappedBy = "payment", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"payment"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentAllocation> allocations;

    @OneToMany(mappedBy = "payment", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"payment"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentSplit> splits;

    @OneToMany(mappedBy = "depositPayment", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"depositPayment"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<VendorDepositApplication> depositApplications;

    @OneToMany(mappedBy = "depositPayment", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"depositPayment"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<CustomerDepositApplication> customerDepositApplications;

    @Column(name = "journal_entry_id")
    private String journalEntryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"payment", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private JournalEntry journalEntry;

    @Column(name = "sales_order_id")
    private String salesOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"depositPayments", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private SalesOrder salesOrder;

    @Column(name = "fiscal_year_id", nullable = false)
    private String fiscalYearId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_year_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"payments", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private FiscalYear fiscalYear;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

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
