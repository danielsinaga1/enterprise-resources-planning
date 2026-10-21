package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel ga_material_request_items. (Dibangkitkan dari model Prisma GaMaterialRequestItem.) */
@Entity
@Table(name = "ga_material_request_items")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaMaterialRequestItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "material_request_id", nullable = false)
    private String materialRequestId;

    @Column(name = "inventory_item_id")
    private String inventoryItemId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "sku")
    private String sku;

    @Column(name = "uom", nullable = false)
    private String uom;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "notes")
    private String notes;

    @Column(name = "vendor_name")
    private String vendorName;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "po_number")
    private String poNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "procurement_status", nullable = false)
    private GaProcurementStatus procurementStatus = GaProcurementStatus.REQUESTED;

    @Column(name = "received_qty", nullable = false, precision = 12, scale = 2)
    private BigDecimal receivedQty = BigDecimal.ZERO;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "received_into_location_id")
    private String receivedIntoLocationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_request_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"items", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaMaterialRequest materialRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"materialRequestItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaInventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_into_location_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"materialRequestItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaLocation receivedIntoLocation;

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
