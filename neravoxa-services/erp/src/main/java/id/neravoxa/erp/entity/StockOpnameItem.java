package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel stock_opname_items. (Dibangkitkan dari model Prisma StockOpnameItem.) */
@Entity
@Table(name = "stock_opname_items")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class StockOpnameItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "stock_opname_id", nullable = false)
    private String stockOpnameId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_opname_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"items", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private StockOpname stockOpname;

    @Column(name = "item_id", nullable = false)
    private String itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"opnameLines", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private InventoryItem item;

    @Column(name = "warehouse_id")
    private String warehouseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"opnameLines", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Warehouse warehouse;

    @Column(name = "system_stock", nullable = false, precision = 15, scale = 3)
    private BigDecimal systemStock;

    @Column(name = "actual_stock", nullable = false, precision = 15, scale = 3)
    private BigDecimal actualStock;

    @Column(name = "difference", nullable = false, precision = 15, scale = 3)
    private BigDecimal difference;

    @Column(name = "unit_cost", nullable = false, precision = 20, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "total_value", nullable = false, precision = 20, scale = 2)
    private BigDecimal totalValue = BigDecimal.ZERO;

    @Column(name = "movement_id")
    private String movementId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
