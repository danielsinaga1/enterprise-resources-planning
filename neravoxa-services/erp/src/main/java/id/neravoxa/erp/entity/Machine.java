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

/** Tabel machines. (Dibangkitkan dari model Prisma Machine.) */
@Entity
@Table(name = "machines")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Machine {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "ideal_rate_per_hour", precision = 15, scale = 3)
    private BigDecimal idealRatePerHour;

    @Column(name = "unit", nullable = false)
    private String unit = "kg";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "notes")
    private String notes;

    @OneToMany(mappedBy = "machine", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"machine"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProductionRun> productionRuns;

    @OneToMany(mappedBy = "machine", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"machine"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProductionRunMachine> runMachines;

    @OneToMany(mappedBy = "machine", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"machine"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProductionDowntime> downtimes;

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
