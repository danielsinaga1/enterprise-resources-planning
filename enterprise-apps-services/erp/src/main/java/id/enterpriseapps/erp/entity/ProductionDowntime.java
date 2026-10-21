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

/** Tabel production_downtimes. (Dibangkitkan dari model Prisma ProductionDowntime.) */
@Entity
@Table(name = "production_downtimes")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class ProductionDowntime {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "production_run_id", nullable = false)
    private String productionRunId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_run_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"downtimes", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private ProductionRun productionRun;

    @Column(name = "machine_id")
    private String machineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"downtimes", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Machine machine;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "reason")
    private String reason;

    @Column(name = "duration_min", nullable = false, precision = 10, scale = 2)
    private BigDecimal durationMin;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
