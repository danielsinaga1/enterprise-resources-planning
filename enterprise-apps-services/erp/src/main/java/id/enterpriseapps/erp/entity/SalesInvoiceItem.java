package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel sales_invoice_items. (Dibangkitkan dari model Prisma SalesInvoiceItem.) */
@Entity
@Table(name = "sales_invoice_items")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class SalesInvoiceItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "sales_invoice_id", nullable = false)
    private String salesInvoiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_invoice_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"items", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private SalesInvoice salesInvoice;

    @Column(name = "item_type", nullable = false)
    private String itemType = "product";

    @Column(name = "inventory_item_id")
    private String inventoryItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"salesInvoiceItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private InventoryItem inventoryItem;

    @Column(name = "warehouse_id")
    private String warehouseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"salesInvoiceItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Warehouse warehouse;

    @Column(name = "service_item_id")
    private String serviceItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"salesInvoiceItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private ServiceItem serviceItem;

    @Column(name = "delivery_order_item_id")
    private String deliveryOrderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_order_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"invoiceItem", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private DeliveryOrderItem deliveryOrderItem;

    @OneToMany(mappedBy = "salesInvoiceItem", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"salesInvoiceItem"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<CogsBackfillQueue> cogsBackfills;

    @OneToMany(mappedBy = "salesInvoiceItem", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"salesInvoiceItem"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<SalesReturnItem> returnItems;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "rate", nullable = false, precision = 15, scale = 2)
    private BigDecimal rate;

    @Column(name = "discount", nullable = false, precision = 5, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "tax_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPct = BigDecimal.ZERO;

    @Column(name = "pph_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal pphPct = BigDecimal.ZERO;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"salesItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account account;

    @Column(name = "description")
    private String description;

    @Column(name = "source_movement_id")
    private String sourceMovementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_movement_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"consumingSalesItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private StockMovement sourceMovement;

    @Column(name = "unit_cost_loaded", precision = 20, scale = 4)
    private BigDecimal unitCostLoaded;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

}
