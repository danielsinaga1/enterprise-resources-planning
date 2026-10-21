package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel fiscal_years. (Dibangkitkan dari model Prisma FiscalYear.) */
@Entity
@Table(name = "fiscal_years")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class FiscalYear {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date", nullable = false)
    private Instant endDate;

    @Column(name = "is_closed", nullable = false)
    private Boolean isClosed = false;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private String closedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"closedYears", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User user;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<AccountingLedgerEntry> ledgerEntries;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<JournalEntry> journalEntries;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<SalesInvoice> salesInvoices;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PurchaseInvoice> purchaseInvoices;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<Quotation> quotations;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<Inquiry> inquiries;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<Payment> payments;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<StockMovement> stockMovements;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProductionRun> productionRuns;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<StockOpname> stockOpnames;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<StockTransfer> stockTransfers;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<DeliveryOrder> deliveryOrders;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<SalesOrder> salesOrders;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PurchaseOrder> purchaseOrders;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GoodsReceipt> goodsReceipts;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<FixedAsset> fixedAssets;

    @OneToMany(mappedBy = "fiscalYear", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"fiscalYear"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<CogsBackfillQueue> cogsBackfills;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

}
