package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel production_run_processes. (Dibangkitkan dari model Prisma ProductionRunProcess.) */
@Entity
@Table(name = "production_run_processes")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class ProductionRunProcess {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "production_run_id", nullable = false)
    private String productionRunId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_run_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"runProcesses", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private ProductionRun productionRun;

    @Column(name = "process_id", nullable = false)
    private String processId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"runProcesses", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private ProductionProcess process;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
