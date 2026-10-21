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

/** Tabel production_runs. (Dibangkitkan dari model Prisma ProductionRun.) */
@Entity
@Table(name = "production_runs")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class ProductionRun {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "run_number", nullable = false)
    private String runNumber;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Column(name = "notes")
    private String notes;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(name = "yield_pct", precision = 5, scale = 2)
    private BigDecimal yieldPct;

    @Column(name = "total_material_cost", precision = 20, scale = 4)
    private BigDecimal totalMaterialCost;

    @Column(name = "total_cost_input_side", precision = 20, scale = 4)
    private BigDecimal totalCostInputSide;

    @Column(name = "total_cost_output_side", precision = 20, scale = 4)
    private BigDecimal totalCostOutputSide;

    @Column(name = "total_byproduct_value", precision = 20, scale = 4)
    private BigDecimal totalByproductValue;

    @Column(name = "computed_hpp", precision = 20, scale = 4)
    private BigDecimal computedHpp;

    @Column(name = "hpp_manual_override", nullable = false)
    private Boolean hppManualOverride = false;

    @Column(name = "machine_id")
    private String machineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"productionRuns", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Machine machine;

    @Column(name = "shift")
    private String shift;

    @Column(name = "shift_lead")
    private String shiftLead;

    @Column(name = "manpower_count")
    private Integer manpowerCount;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "good_qty", precision = 15, scale = 3)
    private BigDecimal goodQty;

    @Column(name = "reject_qty", precision = 15, scale = 3)
    private BigDecimal rejectQty;

    @Column(name = "downtime_min", precision = 10, scale = 2)
    private BigDecimal downtimeMin;

    @Column(name = "availability_pct", precision = 5, scale = 2)
    private BigDecimal availabilityPct;

    @Column(name = "performance_pct", precision = 5, scale = 2)
    private BigDecimal performancePct;

    @Column(name = "quality_pct", precision = 5, scale = 2)
    private BigDecimal qualityPct;

    @Column(name = "oee_pct", precision = 5, scale = 2)
    private BigDecimal oeePct;

    @Column(name = "planned_start")
    private Instant plannedStart;

    @Column(name = "planned_end")
    private Instant plannedEnd;

    @Column(name = "break_start")
    private Instant breakStart;

    @Column(name = "break_end")
    private Instant breakEnd;

    @OneToMany(mappedBy = "productionRun", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"productionRun"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProductionRunMachine> runMachines;

    @OneToMany(mappedBy = "productionRun", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"productionRun"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProductionDowntime> downtimes;

    @OneToMany(mappedBy = "productionRun", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"productionRun"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProductionRunItem> items;

    @OneToMany(mappedBy = "productionRun", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"productionRun"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProductionRunCost> runCosts;

    @OneToMany(mappedBy = "productionRun", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"productionRun"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProductionRunProcess> runProcesses;

    @Column(name = "is_cancelled", nullable = false)
    private Boolean isCancelled = false;

    @Column(name = "fiscal_year_id", nullable = false)
    private String fiscalYearId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_year_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"productionRuns", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private FiscalYear fiscalYear;

    @Column(name = "created_by_id", nullable = false)
    private String createdById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"productionRuns", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User createdBy;

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
