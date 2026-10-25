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

/** Tabel purchase_orders. (Dibangkitkan dari model Prisma PurchaseOrder.) */
@Entity
@Table(name = "purchase_orders")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrder {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "po_number", nullable = false)
    private String poNumber;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Column(name = "party_id")
    private String partyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"purchaseOrders", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Party supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DocumentStatus status = DocumentStatus.Submitted;

    @Column(name = "notes")
    private String notes;

    @Column(name = "terms")
    private String terms;

    @Column(name = "expected_delivery_date")
    private Instant expectedDeliveryDate;

    @Column(name = "total_estimated", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalEstimated = BigDecimal.ZERO;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "other_costs", nullable = false, precision = 15, scale = 2)
    private BigDecimal otherCosts = BigDecimal.ZERO;

    @Column(name = "other_costs_label")
    private String otherCostsLabel;

    @Column(name = "grand_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "payment_term_days")
    private Integer paymentTermDays;

    @Column(name = "warehouse_id")
    private String warehouseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"purchaseOrders", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Warehouse warehouse;

    @Column(name = "supplier_quote_ref")
    private String supplierQuoteRef;

    @Column(name = "approval_status")
    private String approvalStatus;

    @Column(name = "approval_note")
    private String approvalNote;

    @OneToMany(mappedBy = "purchaseOrder", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"purchaseOrder"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PurchaseOrderItem> items;

    @OneToMany(mappedBy = "purchaseOrder", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"purchaseOrder"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GoodsReceipt> goodsReceipts;

    @OneToMany(mappedBy = "purchaseOrder", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"purchaseOrder"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PurchaseInvoice> purchaseInvoices;

    @Column(name = "fiscal_year_id", nullable = false)
    private String fiscalYearId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_year_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"purchaseOrders", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private FiscalYear fiscalYear;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"purchaseOrders", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User user;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "close_reason")
    private String closeReason;

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
