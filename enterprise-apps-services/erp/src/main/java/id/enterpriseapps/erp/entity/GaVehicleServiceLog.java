package id.enterpriseapps.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.annotations.DynamicUpdate;

/** Tabel ga_vehicle_service_logs. (Dibangkitkan dari model Prisma GaVehicleServiceLog.) */
@Entity
@Table(name = "ga_vehicle_service_logs")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaVehicleServiceLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Column(name = "tanggal", nullable = false)
    private Instant tanggal;

    @Column(name = "odometer")
    private Integer odometer;

    @Enumerated(EnumType.STRING)
    @Column(name = "jenis_service", nullable = false)
    private GaVehicleServiceType jenisService;

    @Column(name = "deskripsi", nullable = false)
    private String deskripsi;

    @Column(name = "bengkel")
    private String bengkel;

    @Column(name = "biaya", precision = 14, scale = 2)
    private BigDecimal biaya;

    @Column(name = "work_order_id")
    private String workOrderId;

    @Column(name = "journal_entry_id")
    private String journalEntryId;

    @Column(name = "purchase_invoice_id")
    private String purchaseInvoiceId;

    @Column(name = "account_id")
    private String accountId;

    @Column(name = "credit_account_id")
    private String creditAccountId;

    @Column(name = "source", nullable = false)
    private String source = "EAM";

    @Column(name = "external_ref")
    private String externalRef;

    @Column(name = "created_by_id")
    private String createdById;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"vehicleServiceLogs", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAsset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"vehicleServiceLog", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaWorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"gaVehicleServiceLog", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_invoice_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"gaServiceLogs", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private PurchaseInvoice purchaseInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"gaVehicleServiceExpense", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"gaVehicleServiceCredit", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account creditAccount;

    /** Sisi balik relasi 1:1 (GaVehicleServiceRequest.serviceLog). Fetch/JOIN FETCH lewat field ini; JSON memakai serviceRequest. */
    @OneToMany(mappedBy = "serviceLog", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GaVehicleServiceRequest> serviceRequestRef;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }

    /** GaVehicleServiceRequest terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("serviceRequest")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"serviceLog", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public GaVehicleServiceRequest getServiceRequest() {
        if (serviceRequestRef == null || !Hibernate.isInitialized(serviceRequestRef) || serviceRequestRef.isEmpty()) return null;
        return serviceRequestRef.get(0);
    }

}
