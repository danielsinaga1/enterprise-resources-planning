package id.neravoxa.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel ga_cost_centers. (Dibangkitkan dari model Prisma GaCostCenter.) */
@Entity
@Table(name = "ga_cost_centers")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaCostCenter {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "branch_id", nullable = false)
    private String branchId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"costCenters", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaBranch branch;

    @OneToMany(mappedBy = "costCenter", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"costCenter"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaAsset> assets;

    @OneToMany(mappedBy = "costCenter", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"costCenter"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentRequest> paymentRequests;

    @OneToMany(mappedBy = "costCenter", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"costCenter"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentRequestLine> paymentRequestLines;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

}
