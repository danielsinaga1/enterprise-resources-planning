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

/** Tabel sales_order_items. (Dibangkitkan dari model Prisma SalesOrderItem.) */
@Entity
@Table(name = "sales_order_items")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class SalesOrderItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "sales_order_id", nullable = false)
    private String salesOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"items", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private SalesOrder salesOrder;

    @Column(name = "inventory_item_id")
    private String inventoryItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"salesOrderItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private InventoryItem inventoryItem;

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

    @Column(name = "description")
    private String description;

    @Column(name = "delivered_qty", nullable = false, precision = 15, scale = 3)
    private BigDecimal deliveredQty = BigDecimal.ZERO;

    @OneToMany(mappedBy = "salesOrderItem", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"salesOrderItem"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<DeliveryOrderItem> deliveryOrderItems;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
