package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel stock_movements. (Dibangkitkan dari model Prisma StockMovement.) */
@Entity
@Table(name = "stock_movements")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class StockMovement {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "movement_number", nullable = false)
    private String movementNumber;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Column(name = "item_id", nullable = false)
    private String itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"movements", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private InventoryItem item;

    @Column(name = "warehouse_id")
    private String warehouseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"movements", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private StockMovementType movementType;

    @Column(name = "quantity", nullable = false, precision = 20, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 20, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "total_value", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalValue = BigDecimal.ZERO;

    @Column(name = "notes")
    private String notes;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "offset_account_id")
    private String offsetAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offset_account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"movementsAsOffset", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account offsetAccount;

    @Column(name = "journal_entry_id")
    private String journalEntryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"stockMovement", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private JournalEntry journalEntry;

    @Column(name = "fiscal_year_id", nullable = false)
    private String fiscalYearId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_year_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"stockMovements", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private FiscalYear fiscalYear;

    @Column(name = "created_by_id", nullable = false)
    private String createdById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"stockMovements", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User createdBy;

    @Column(name = "is_cancelled", nullable = false)
    private Boolean isCancelled = false;

    @Column(name = "unit_cost_loaded", precision = 20, scale = 4)
    private BigDecimal unitCostLoaded;

    @Column(name = "source_movement_id")
    private String sourceMovementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_movement_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"consumingMovements", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private StockMovement sourceMovement;

    @OneToMany(mappedBy = "sourceMovement", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"sourceMovement"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<StockMovement> consumingMovements;

    /** Sisi balik relasi 1:1 (DeliveryOrderItem.stockMovement). Fetch/JOIN FETCH lewat field ini; JSON memakai deliveryOrderItem. */
    @OneToMany(mappedBy = "stockMovement", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<DeliveryOrderItem> deliveryOrderItemRef;

    /** Sisi balik relasi 1:1 (GoodsReceiptItem.stockMovement). Fetch/JOIN FETCH lewat field ini; JSON memakai goodsReceiptItem. */
    @OneToMany(mappedBy = "stockMovement", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GoodsReceiptItem> goodsReceiptItemRef;

    @OneToMany(mappedBy = "sourceMovement", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"sourceMovement"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProductionRunItem> productionInputBatchUsage;

    @OneToMany(mappedBy = "sourceMovement", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"sourceMovement"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<SalesInvoiceItem> consumingSalesItems;

    @OneToMany(mappedBy = "sourceMovement", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"sourceMovement"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<StockTransferItem> consumingTransferItems;

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

    /** DeliveryOrderItem terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("deliveryOrderItem")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"stockMovement", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public DeliveryOrderItem getDeliveryOrderItem() {
        if (deliveryOrderItemRef == null || !Hibernate.isInitialized(deliveryOrderItemRef) || deliveryOrderItemRef.isEmpty()) return null;
        return deliveryOrderItemRef.get(0);
    }

    /** GoodsReceiptItem terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("goodsReceiptItem")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"stockMovement", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public GoodsReceiptItem getGoodsReceiptItem() {
        if (goodsReceiptItemRef == null || !Hibernate.isInitialized(goodsReceiptItemRef) || goodsReceiptItemRef.isEmpty()) return null;
        return goodsReceiptItemRef.get(0);
    }

}
