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

/** Tabel production_run_costs. (Dibangkitkan dari model Prisma ProductionRunCost.) */
@Entity
@Table(name = "production_run_costs")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class ProductionRunCost {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "production_run_id", nullable = false)
    private String productionRunId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_run_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"runCosts", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private ProductionRun productionRun;

    @Column(name = "standard_id")
    private String standardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "standard_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"runCosts", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private ProductionCostStandard standard;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "cost_side", nullable = false)
    private String costSide;

    @Column(name = "basis", nullable = false)
    private String basis;

    @Column(name = "rate", nullable = false, precision = 15, scale = 4)
    private BigDecimal rate;

    @Column(name = "qty_basis", nullable = false, precision = 15, scale = 3)
    private BigDecimal qtyBasis;

    @Column(name = "total_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "cost_account_id")
    private String costAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"productionRunCosts", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account costAccount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
