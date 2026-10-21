package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel goods_receipt_items. (Dibangkitkan dari model Prisma GoodsReceiptItem.) */
@Entity
@Table(name = "goods_receipt_items")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GoodsReceiptItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "goods_receipt_id", nullable = false)
    private String goodsReceiptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_receipt_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"items", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GoodsReceipt goodsReceipt;

    @Column(name = "inventory_item_id", nullable = false)
    private String inventoryItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"goodsReceiptItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private InventoryItem inventoryItem;

    @Column(name = "warehouse_id", nullable = false)
    private String warehouseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"goodsReceiptItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Warehouse warehouse;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "locked_unit_cost", nullable = false, precision = 20, scale = 2)
    private BigDecimal lockedUnitCost = BigDecimal.ZERO;

    @Column(name = "description")
    private String description;

    @Column(name = "purchase_order_item_id")
    private String purchaseOrderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"goodsReceiptItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private PurchaseOrderItem purchaseOrderItem;

    @Column(name = "stock_movement_id")
    private String stockMovementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_movement_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"goodsReceiptItem", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private StockMovement stockMovement;

    /** Sisi balik relasi 1:1 (PurchaseInvoiceItem.goodsReceiptItem). Fetch/JOIN FETCH lewat field ini; JSON memakai invoiceItem. */
    @OneToMany(mappedBy = "goodsReceiptItem", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<PurchaseInvoiceItem> invoiceItemRef;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

    /** PurchaseInvoiceItem terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("invoiceItem")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"goodsReceiptItem", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public PurchaseInvoiceItem getInvoiceItem() {
        if (invoiceItemRef == null || !Hibernate.isInitialized(invoiceItemRef) || invoiceItemRef.isEmpty()) return null;
        return invoiceItemRef.get(0);
    }

}
