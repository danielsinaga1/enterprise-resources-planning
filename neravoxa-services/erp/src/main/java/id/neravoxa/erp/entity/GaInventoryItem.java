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

/** Tabel ga_inventory_items. (Dibangkitkan dari model Prisma GaInventoryItem.) */
@Entity
@Table(name = "ga_inventory_items")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaInventoryItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category_id", nullable = false)
    private String categoryId;

    @Column(name = "uom", nullable = false)
    private String uom;

    @Column(name = "min_stock", nullable = false, precision = 12, scale = 2)
    private BigDecimal minStock = BigDecimal.ZERO;

    @Column(name = "image_path")
    private String imagePath;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"items", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaInventoryCategory category;

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"item"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaStockByLocation> stocks;

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"item"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaInventoryMovement> movements;

    @OneToMany(mappedBy = "inventoryItem", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"inventoryItem"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaWorkOrderPart> workOrderParts;

    @OneToMany(mappedBy = "inventoryItem", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"inventoryItem"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaMaterialRequestItem> materialRequestItems;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
