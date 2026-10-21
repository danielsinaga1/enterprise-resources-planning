package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel ga_locations. (Dibangkitkan dari model Prisma GaLocation.) */
@Entity
@Table(name = "ga_locations")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaLocation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "branch_id", nullable = false)
    private String branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"locations", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaBranch branch;

    @OneToMany(mappedBy = "location", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"location"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaStockByLocation> stocks;

    @OneToMany(mappedBy = "fromLocation", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fromLocation"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaInventoryMovement> movsFrom;

    @OneToMany(mappedBy = "toLocation", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"toLocation"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaInventoryMovement> movsTo;

    @OneToMany(mappedBy = "fromLocation", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fromLocation"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaWorkOrderPart> workOrderParts;

    @OneToMany(mappedBy = "receivedIntoLocation", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"receivedIntoLocation"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaMaterialRequestItem> materialRequestItems;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

}
