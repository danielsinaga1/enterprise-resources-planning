package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel purchase_order_items. (Dibangkitkan dari model Prisma PurchaseOrderItem.) */
@Entity
@Table(name = "purchase_order_items")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "purchase_order_id", nullable = false)
    private String purchaseOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"items", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private PurchaseOrder purchaseOrder;

    @Column(name = "inventory_item_id")
    private String inventoryItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"purchaseOrderItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private InventoryItem inventoryItem;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "estimated_rate", nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedRate = BigDecimal.ZERO;

    @Column(name = "estimated_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedAmount = BigDecimal.ZERO;

    @Column(name = "discount", nullable = false, precision = 5, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "tax_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPct = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "qty_received", nullable = false, precision = 15, scale = 3)
    private BigDecimal qtyReceived = BigDecimal.ZERO;

    @Column(name = "description")
    private String description;

    @OneToMany(mappedBy = "purchaseOrderItem", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"purchaseOrderItem"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GoodsReceiptItem> goodsReceiptItems;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

}
