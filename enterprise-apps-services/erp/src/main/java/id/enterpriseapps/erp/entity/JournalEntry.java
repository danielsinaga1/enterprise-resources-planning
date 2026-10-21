package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel journal_entries. (Dibangkitkan dari model Prisma JournalEntry.) */
@Entity
@Table(name = "journal_entries")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class JournalEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "entry_number", nullable = false)
    private String entryNumber;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DocumentStatus status = DocumentStatus.Draft;

    @Column(name = "narration")
    private String narration;

    @Column(name = "fiscal_year_id", nullable = false)
    private String fiscalYearId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_year_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"journalEntries", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private FiscalYear fiscalYear;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"journalEntries", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User user;

    @OneToMany(mappedBy = "journalEntry", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"journalEntry"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<JournalItem> items;

    /** Sisi balik relasi 1:1 (StockMovement.journalEntry). Fetch/JOIN FETCH lewat field ini; JSON memakai stockMovement. */
    @OneToMany(mappedBy = "journalEntry", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<StockMovement> stockMovementRef;

    /** Sisi balik relasi 1:1 (Payment.journalEntry). Fetch/JOIN FETCH lewat field ini; JSON memakai payment. */
    @OneToMany(mappedBy = "journalEntry", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Payment> paymentRef;

    /** Sisi balik relasi 1:1 (VendorDepositApplication.journalEntry). Fetch/JOIN FETCH lewat field ini; JSON memakai depositApplication. */
    @OneToMany(mappedBy = "journalEntry", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<VendorDepositApplication> depositApplicationRef;

    /** Sisi balik relasi 1:1 (CustomerDepositApplication.journalEntry). Fetch/JOIN FETCH lewat field ini; JSON memakai customerDepositApplication. */
    @OneToMany(mappedBy = "journalEntry", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<CustomerDepositApplication> customerDepositApplicationRef;

    @OneToMany(mappedBy = "journalEntry", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"journalEntry"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<DepreciationEntry> depreciationEntries;

    @OneToMany(mappedBy = "journalEntry", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"journalEntry"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<CogsBackfillSettlement> cogsBackfillSettlements;

    /** Sisi balik relasi 1:1 (GaVehicleServiceLog.journalEntry). Fetch/JOIN FETCH lewat field ini; JSON memakai gaVehicleServiceLog. */
    @OneToMany(mappedBy = "journalEntry", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GaVehicleServiceLog> gaVehicleServiceLogRef;

    /** Sisi balik relasi 1:1 (GaVehicleTax.journalEntry). Fetch/JOIN FETCH lewat field ini; JSON memakai gaVehicleTax. */
    @OneToMany(mappedBy = "journalEntry", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GaVehicleTax> gaVehicleTaxRef;

    /** Sisi balik relasi 1:1 (GaMaterialRequest.journalEntry). Fetch/JOIN FETCH lewat field ini; JSON memakai gaMaterialRequest. */
    @OneToMany(mappedBy = "journalEntry", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GaMaterialRequest> gaMaterialRequestRef;

    /** Sisi balik relasi 1:1 (PaymentRequest.journalEntry). Fetch/JOIN FETCH lewat field ini; JSON memakai paymentRequest. */
    @OneToMany(mappedBy = "journalEntry", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<PaymentRequest> paymentRequestRef;

    /** Sisi balik relasi 1:1 (PaymentRequest.settlementJournal). Fetch/JOIN FETCH lewat field ini; JSON memakai paymentRequestSettlement. */
    @OneToMany(mappedBy = "settlementJournal", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<PaymentRequest> paymentRequestSettlementRef;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "is_dummy", nullable = false)
    private Boolean isDummy = false;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

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

    /** StockMovement terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("stockMovement")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"journalEntry", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public StockMovement getStockMovement() {
        if (stockMovementRef == null || !Hibernate.isInitialized(stockMovementRef) || stockMovementRef.isEmpty()) return null;
        return stockMovementRef.get(0);
    }

    /** Payment terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("payment")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"journalEntry", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public Payment getPayment() {
        if (paymentRef == null || !Hibernate.isInitialized(paymentRef) || paymentRef.isEmpty()) return null;
        return paymentRef.get(0);
    }

    /** VendorDepositApplication terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("depositApplication")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"journalEntry", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public VendorDepositApplication getDepositApplication() {
        if (depositApplicationRef == null || !Hibernate.isInitialized(depositApplicationRef) || depositApplicationRef.isEmpty()) return null;
        return depositApplicationRef.get(0);
    }

    /** CustomerDepositApplication terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("customerDepositApplication")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"journalEntry", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public CustomerDepositApplication getCustomerDepositApplication() {
        if (customerDepositApplicationRef == null || !Hibernate.isInitialized(customerDepositApplicationRef) || customerDepositApplicationRef.isEmpty()) return null;
        return customerDepositApplicationRef.get(0);
    }

    /** GaVehicleServiceLog terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("gaVehicleServiceLog")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"journalEntry", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public GaVehicleServiceLog getGaVehicleServiceLog() {
        if (gaVehicleServiceLogRef == null || !Hibernate.isInitialized(gaVehicleServiceLogRef) || gaVehicleServiceLogRef.isEmpty()) return null;
        return gaVehicleServiceLogRef.get(0);
    }

    /** GaVehicleTax terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("gaVehicleTax")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"journalEntry", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public GaVehicleTax getGaVehicleTax() {
        if (gaVehicleTaxRef == null || !Hibernate.isInitialized(gaVehicleTaxRef) || gaVehicleTaxRef.isEmpty()) return null;
        return gaVehicleTaxRef.get(0);
    }

    /** GaMaterialRequest terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("gaMaterialRequest")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"journalEntry", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public GaMaterialRequest getGaMaterialRequest() {
        if (gaMaterialRequestRef == null || !Hibernate.isInitialized(gaMaterialRequestRef) || gaMaterialRequestRef.isEmpty()) return null;
        return gaMaterialRequestRef.get(0);
    }

    /** PaymentRequest terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("paymentRequest")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"journalEntry", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public PaymentRequest getPaymentRequest() {
        if (paymentRequestRef == null || !Hibernate.isInitialized(paymentRequestRef) || paymentRequestRef.isEmpty()) return null;
        return paymentRequestRef.get(0);
    }

    /** PaymentRequest terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("paymentRequestSettlement")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"settlementJournal", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public PaymentRequest getPaymentRequestSettlement() {
        if (paymentRequestSettlementRef == null || !Hibernate.isInitialized(paymentRequestSettlementRef) || paymentRequestSettlementRef.isEmpty()) return null;
        return paymentRequestSettlementRef.get(0);
    }

}
