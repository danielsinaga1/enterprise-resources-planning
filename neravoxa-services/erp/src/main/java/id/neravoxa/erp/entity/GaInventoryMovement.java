package id.neravoxa.erp.entity;

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

/** Tabel ga_inventory_movements. (Dibangkitkan dari model Prisma GaInventoryMovement.) */
@Entity
@Table(name = "ga_inventory_movements")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaInventoryMovement {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "item_id", nullable = false)
    private String itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private GaMovementType type;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "from_location_id")
    private String fromLocationId;

    @Column(name = "to_location_id")
    private String toLocationId;

    @Column(name = "reference_no")
    private String referenceNo;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"movements", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaInventoryItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_location_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"movsFrom", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaLocation fromLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_location_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"movsTo", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaLocation toLocation;

    /** Sisi balik relasi 1:1 (GaWorkOrderPart.movement). Fetch/JOIN FETCH lewat field ini; JSON memakai workOrderPart. */
    @OneToMany(mappedBy = "movement", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GaWorkOrderPart> workOrderPartRef;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    /** GaWorkOrderPart terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("workOrderPart")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"movement", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public GaWorkOrderPart getWorkOrderPart() {
        if (workOrderPartRef == null || !Hibernate.isInitialized(workOrderPartRef) || workOrderPartRef.isEmpty()) return null;
        return workOrderPartRef.get(0);
    }

}
