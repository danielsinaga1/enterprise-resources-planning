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

/** Tabel delivery_order_items. (Dibangkitkan dari model Prisma DeliveryOrderItem.) */
@Entity
@Table(name = "delivery_order_items")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class DeliveryOrderItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "delivery_order_id", nullable = false)
    private String deliveryOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_order_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"items", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private DeliveryOrder deliveryOrder;

    @Column(name = "inventory_item_id")
    private String inventoryItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"deliveryOrderItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private InventoryItem inventoryItem;

    @Column(name = "warehouse_id")
    private String warehouseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"deliveryOrderItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Warehouse warehouse;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "description")
    private String description;

    @Column(name = "stock_movement_id")
    private String stockMovementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_movement_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"deliveryOrderItem", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private StockMovement stockMovement;

    @Column(name = "sales_order_item_id")
    private String salesOrderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"deliveryOrderItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private SalesOrderItem salesOrderItem;

    /** Sisi balik relasi 1:1 (SalesInvoiceItem.deliveryOrderItem). Fetch/JOIN FETCH lewat field ini; JSON memakai invoiceItem. */
    @OneToMany(mappedBy = "deliveryOrderItem", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<SalesInvoiceItem> invoiceItemRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    /** SalesInvoiceItem terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("invoiceItem")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"deliveryOrderItem", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public SalesInvoiceItem getInvoiceItem() {
        if (invoiceItemRef == null || !Hibernate.isInitialized(invoiceItemRef) || invoiceItemRef.isEmpty()) return null;
        return invoiceItemRef.get(0);
    }

}
