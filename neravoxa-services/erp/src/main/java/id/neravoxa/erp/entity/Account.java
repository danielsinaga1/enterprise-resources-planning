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

/** Tabel accounts. (Dibangkitkan dari model Prisma Account.) */
@Entity
@Table(name = "accounts")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Account {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "root_type", nullable = false)
    private RootType rootType;

    @Column(name = "is_group", nullable = false)
    private Boolean isGroup = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false)
    private String currency = "IDR";

    @Column(name = "description")
    private String description;

    @Column(name = "parent_id")
    private String parentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"children", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"parent"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<Account> children;

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"account"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<AccountingLedgerEntry> ledgerEntries;

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"account"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<JournalItem> journalItems;

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"account"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<SalesInvoiceItem> salesItems;

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"account"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PurchaseInvoiceItem> purchaseItems;

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"account"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<QuotationItem> quotationItems;

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"account"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<Payment> payments;

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"account"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentSplit> paymentSplits;

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"account"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<InventoryItem> inventoryItems;

    @OneToMany(mappedBy = "offsetAccount", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"offsetAccount"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<StockMovement> movementsAsOffset;

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"account"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<BankReconciliation> reconciliations;

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"account"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ServiceItem> serviceItems;

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"account"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<SystemAccountMapping> systemAccountMappings;

    @OneToMany(mappedBy = "assetAccount", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"assetAccount"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<FixedAsset> fixedAssetsAsAsset;

    @OneToMany(mappedBy = "depreciationAccount", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"depreciationAccount"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<FixedAsset> fixedAssetsAsDepr;

    @OneToMany(mappedBy = "accumulatedDepAccount", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"accumulatedDepAccount"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<FixedAsset> fixedAssetsAsAccDep;

    @OneToMany(mappedBy = "costAccount", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"costAccount"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProductionCostStandard> productionCostStandards;

    @OneToMany(mappedBy = "costAccount", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"costAccount"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProductionRunCost> productionRunCosts;

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"account"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleServiceLog> gaVehicleServiceExpense;

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"account"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleTax> gaVehicleTaxExpense;

    @OneToMany(mappedBy = "creditAccount", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"creditAccount"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleTax> gaVehicleTaxCredit;

    @OneToMany(mappedBy = "glAccount", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"glAccount"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaMaterialRequest> gaMaterialRequestExpense;

    @OneToMany(mappedBy = "creditAccount", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"creditAccount"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaMaterialRequest> gaMaterialRequestCredit;

    @OneToMany(mappedBy = "creditAccount", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"creditAccount"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<GaVehicleServiceLog> gaVehicleServiceCredit;

    @OneToMany(mappedBy = "defaultExpenseAccount", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"defaultExpenseAccount"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentRequestType> paymentRequestTypes;

    @OneToMany(mappedBy = "expenseAccount", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"expenseAccount"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentRequestLine> paymentRequestLines;

    @OneToMany(mappedBy = "cashAccount", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"cashAccount"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentRequest> paymentRequestsPaidFrom;

    @OneToMany(mappedBy = "settlementCashAccount", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"settlementCashAccount"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentRequest> paymentRequestsSettledFrom;

    @OneToMany(mappedBy = "expenseAccount", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"expenseAccount"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<PaymentRequestSettlementLine> paymentRequestSettlementLines;

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
