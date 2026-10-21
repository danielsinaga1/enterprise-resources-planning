package id.enterpriseapps.erp.entity;

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

/** Tabel ga_material_requests. (Dibangkitkan dari model Prisma GaMaterialRequest.) */
@Entity
@Table(name = "ga_material_requests")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaMaterialRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "code", nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GaMaterialRequestStatus status = GaMaterialRequestStatus.DRAFT;

    @Column(name = "work_order_id")
    private String workOrderId;

    @Column(name = "asset_id")
    private String assetId;

    @Column(name = "machine_target")
    private String machineTarget;

    @Column(name = "notes")
    private String notes;

    @Column(name = "requested_by_id", nullable = false)
    private String requestedById;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_by_id")
    private String approvedById;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_by_id")
    private String rejectedById;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "purchase_order_id")
    private String purchaseOrderId;

    @Column(name = "gl_account_id")
    private String glAccountId;

    @Column(name = "journal_entry_id")
    private String journalEntryId;

    @Column(name = "purchase_invoice_id")
    private String purchaseInvoiceId;

    @Column(name = "credit_account_id")
    private String creditAccountId;

    @Column(name = "paid_by_id")
    private String paidById;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_amount", precision = 14, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"materialRequests", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaWorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"materialRequests", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAsset asset;

    @OneToMany(mappedBy = "materialRequest", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"materialRequest"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaMaterialRequestItem> items;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"gaMaterialRequest", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_invoice_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"gaMaterialRequests", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private PurchaseInvoice purchaseInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gl_account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"gaMaterialRequestExpense", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account glAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"gaMaterialRequestCredit", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account creditAccount;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (requestedAt == null) requestedAt = Instant.now();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onPreUpdate() {
        updatedAt = Instant.now();
    }

}
