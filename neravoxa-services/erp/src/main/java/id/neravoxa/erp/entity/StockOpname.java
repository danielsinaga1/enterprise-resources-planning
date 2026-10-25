package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel stock_opnames. (Dibangkitkan dari model Prisma StockOpname.) */
@Entity
@Table(name = "stock_opnames")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class StockOpname {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "opname_number", nullable = false)
    private String opnameNumber;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StockOpnameStatus status = StockOpnameStatus.Draft;

    @Column(name = "notes")
    private String notes;

    @Column(name = "warehouse_id")
    private String warehouseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"opnames", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Warehouse warehouse;

    @OneToMany(mappedBy = "stockOpname", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"stockOpname"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<StockOpnameItem> items;

    @Column(name = "fiscal_year_id", nullable = false)
    private String fiscalYearId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_year_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"stockOpnames", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private FiscalYear fiscalYear;

    @Column(name = "created_by_id", nullable = false)
    private String createdById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"stockOpnames", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User createdBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
