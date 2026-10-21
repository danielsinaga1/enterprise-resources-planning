package id.enterpriseapps.erp.entity;

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

/** Tabel cogs_backfill_queue. (Dibangkitkan dari model Prisma CogsBackfillQueue.) */
@Entity
@Table(name = "cogs_backfill_queue")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class CogsBackfillQueue {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "sales_invoice_id", nullable = false)
    private String salesInvoiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_invoice_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"cogsBackfills", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private SalesInvoice salesInvoice;

    @Column(name = "sales_invoice_item_id", nullable = false)
    private String salesInvoiceItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_invoice_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"cogsBackfills", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private SalesInvoiceItem salesInvoiceItem;

    @Column(name = "inventory_item_id", nullable = false)
    private String inventoryItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"cogsBackfills", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private InventoryItem inventoryItem;

    @Column(name = "qty_pending", nullable = false, precision = 15, scale = 3)
    private BigDecimal qtyPending;

    @Column(name = "qty_original", nullable = false, precision = 15, scale = 3)
    private BigDecimal qtyOriginal;

    @Column(name = "cost_at_sale", nullable = false, precision = 20, scale = 2)
    private BigDecimal costAtSale;

    @Column(name = "status", nullable = false)
    private String status = "Pending";

    @Column(name = "fiscal_year_id", nullable = false)
    private String fiscalYearId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_year_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"cogsBackfills", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private FiscalYear fiscalYear;

    @OneToMany(mappedBy = "queue", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"queue"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<CogsBackfillSettlement> settlements;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
