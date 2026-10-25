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

/** Tabel fixed_assets. (Dibangkitkan dari model Prisma FixedAsset.) */
@Entity
@Table(name = "fixed_assets")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class FixedAsset {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "asset_number", nullable = false)
    private String assetNumber;

    @Column(name = "ga_asset_id")
    private String gaAssetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ga_asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"fixedAsset", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAsset gaAsset;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "description")
    private String description;

    @Column(name = "acquisition_date", nullable = false)
    private Instant acquisitionDate;

    @Column(name = "acquisition_cost", nullable = false, precision = 20, scale = 2)
    private BigDecimal acquisitionCost;

    @Column(name = "useful_life_months", nullable = false)
    private Integer usefulLifeMonths;

    @Column(name = "salvage_value", nullable = false, precision = 20, scale = 2)
    private BigDecimal salvageValue = BigDecimal.ZERO;

    @Column(name = "depreciation_method", nullable = false)
    private String depreciationMethod = "straight_line";

    @Column(name = "accumulated_depreciation", nullable = false, precision = 20, scale = 2)
    private BigDecimal accumulatedDepreciation = BigDecimal.ZERO;

    @Column(name = "book_value", nullable = false, precision = 20, scale = 2)
    private BigDecimal bookValue;

    @Column(name = "status", nullable = false)
    private String status = "Active";

    @Column(name = "disposal_date")
    private Instant disposalDate;

    @Column(name = "disposal_amount", precision = 20, scale = 2)
    private BigDecimal disposalAmount;

    @Column(name = "asset_account_id", nullable = false)
    private String assetAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"fixedAssetsAsAsset", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account assetAccount;

    @Column(name = "depreciation_account_id", nullable = false)
    private String depreciationAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depreciation_account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"fixedAssetsAsDepr", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account depreciationAccount;

    @Column(name = "accumulated_dep_account_id", nullable = false)
    private String accumulatedDepAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accumulated_dep_account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"fixedAssetsAsAccDep", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account accumulatedDepAccount;

    @Column(name = "fiscal_year_id", nullable = false)
    private String fiscalYearId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_year_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"fixedAssets", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private FiscalYear fiscalYear;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"fixedAssets", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User user;

    @OneToMany(mappedBy = "fixedAsset", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fixedAsset"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<DepreciationEntry> depreciationEntries;

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
