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

/** Tabel production_run_machines. (Dibangkitkan dari model Prisma ProductionRunMachine.) */
@Entity
@Table(name = "production_run_machines")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class ProductionRunMachine {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "production_run_id", nullable = false)
    private String productionRunId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_run_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"runMachines", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private ProductionRun productionRun;

    @Column(name = "machine_id", nullable = false)
    private String machineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"runMachines", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Machine machine;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "run_min", precision = 10, scale = 2)
    private BigDecimal runMin;

    @Column(name = "downtime_min", precision = 10, scale = 2)
    private BigDecimal downtimeMin;

    @Column(name = "allocated_qty", precision = 15, scale = 3)
    private BigDecimal allocatedQty;

    @Column(name = "availability_pct", precision = 5, scale = 2)
    private BigDecimal availabilityPct;

    @Column(name = "performance_pct", precision = 5, scale = 2)
    private BigDecimal performancePct;

    @Column(name = "quality_pct", precision = 5, scale = 2)
    private BigDecimal qualityPct;

    @Column(name = "oee_pct", precision = 5, scale = 2)
    private BigDecimal oeePct;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
