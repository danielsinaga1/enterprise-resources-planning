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

/** Tabel stock_transfer_items. (Dibangkitkan dari model Prisma StockTransferItem.) */
@Entity
@Table(name = "stock_transfer_items")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class StockTransferItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "transfer_id", nullable = false)
    private String transferId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"items", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private StockTransfer transfer;

    @Column(name = "item_id", nullable = false)
    private String itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"transferLines", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private InventoryItem item;

    @Column(name = "quantity", nullable = false, precision = 20, scale = 3)
    private BigDecimal quantity;

    @Column(name = "description")
    private String description;

    @Column(name = "source_movement_id")
    private String sourceMovementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_movement_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"consumingTransferItems", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private StockMovement sourceMovement;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
