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

/** Tabel inventory_items. (Dibangkitkan dari model Prisma InventoryItem.) */
@Entity
@Table(name = "inventory_items")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class InventoryItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "category")
    private String category;

    @Column(name = "description")
    private String description;

    @Column(name = "current_stock", nullable = false, precision = 15, scale = 3)
    private BigDecimal currentStock = BigDecimal.ZERO;

    @Column(name = "average_cost", nullable = false, precision = 20, scale = 2)
    private BigDecimal averageCost = BigDecimal.ZERO;

    @Column(name = "minimum_stock", nullable = false, precision = 15, scale = 3)
    private BigDecimal minimumStock = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_dummy", nullable = false)
    private Boolean isDummy = false;

    @Column(name = "account_id")
    private String accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"inventoryItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account account;

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"item"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<InventoryStock> stocks;

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"item"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<StockMovement> movements;

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"item"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProductionRunItem> productionLines;

    @OneToMany(mappedBy = "inventoryItem", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"inventoryItem"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<SalesInvoiceItem> salesInvoiceItems;

    @OneToMany(mappedBy = "inventoryItem", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"inventoryItem"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PurchaseInvoiceItem> purchaseInvoiceItems;

    @OneToMany(mappedBy = "inventoryItem", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"inventoryItem"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<QuotationItem> quotationItems;

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"item"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<StockOpnameItem> opnameLines;

    @OneToMany(mappedBy = "inventoryItem", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"inventoryItem"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<CogsBackfillQueue> cogsBackfills;

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"item"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<StockTransferItem> transferLines;

    @OneToMany(mappedBy = "inventoryItem", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"inventoryItem"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<DeliveryOrderItem> deliveryOrderItems;

    @OneToMany(mappedBy = "inventoryItem", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"inventoryItem"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<SalesOrderItem> salesOrderItems;

    @OneToMany(mappedBy = "inventoryItem", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"inventoryItem"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PurchaseOrderItem> purchaseOrderItems;

    @OneToMany(mappedBy = "inventoryItem", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"inventoryItem"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GoodsReceiptItem> goodsReceiptItems;

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"item"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProductionCostStandard> costStandards;

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
