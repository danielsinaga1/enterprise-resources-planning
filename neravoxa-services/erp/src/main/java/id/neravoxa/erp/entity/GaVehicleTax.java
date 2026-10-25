package id.neravoxa.erp.entity;

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

/** Tabel ga_vehicle_taxes. (Dibangkitkan dari model Prisma GaVehicleTax.) */
@Entity
@Table(name = "ga_vehicle_taxes")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class GaVehicleTax {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "asset_id", nullable = false)
    private String assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private GaVehicleTaxType type;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "due_date", nullable = false)
    private Instant dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GaVehicleTaxStatus status = GaVehicleTaxStatus.ACTIVE;

    @Column(name = "document_id")
    private String documentId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_by_id")
    private String createdById;

    @Column(name = "paid_amount", precision = 14, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "paid_by_id")
    private String paidById;

    @Column(name = "journal_entry_id")
    private String journalEntryId;

    @Column(name = "purchase_invoice_id")
    private String purchaseInvoiceId;

    @Column(name = "account_id")
    private String accountId;

    @Column(name = "credit_account_id")
    private String creditAccountId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"vehicleTaxes", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaAsset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"vehicleTaxes", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private GaDocument document;

    /** Sisi balik relasi 1:1 (GaVehicleTaxRequest.tax). Fetch/JOIN FETCH lewat field ini; JSON memakai request. */
    @OneToMany(mappedBy = "tax", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<GaVehicleTaxRequest> requestRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"gaVehicleTax", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private JournalEntry journalEntry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_invoice_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"gaVehicleTaxes", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private PurchaseInvoice purchaseInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"gaVehicleTaxExpense", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_account_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"gaVehicleTaxCredit", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Account creditAccount;

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

    /** GaVehicleTaxRequest terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("request")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"tax", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public GaVehicleTaxRequest getRequest() {
        if (requestRef == null || !Hibernate.isInitialized(requestRef) || requestRef.isEmpty()) return null;
        return requestRef.get(0);
    }

}
