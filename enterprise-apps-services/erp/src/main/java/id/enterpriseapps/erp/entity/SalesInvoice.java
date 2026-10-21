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

/** Tabel sales_invoices. (Dibangkitkan dari model Prisma SalesInvoice.) */
@Entity
@Table(name = "sales_invoices")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class SalesInvoice {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Column(name = "party_id", nullable = false)
    private String partyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"salesInvoices", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Party customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status = InvoiceStatus.Draft;

    @Column(name = "grand_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "outstanding", nullable = false, precision = 15, scale = 2)
    private BigDecimal outstanding;

    @Column(name = "tax_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPct = BigDecimal.ZERO;

    @Column(name = "potongan", nullable = false, precision = 15, scale = 2)
    private BigDecimal potongan = BigDecimal.ZERO;

    @Column(name = "biaya_lain", nullable = false, precision = 15, scale = 2)
    private BigDecimal biayaLain = BigDecimal.ZERO;

    @Column(name = "label_potongan")
    private String labelPotongan;

    @Column(name = "label_biaya")
    private String labelBiaya;

    @Column(name = "terms")
    private String terms;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "notes")
    private String notes;

    @Column(name = "customer_ref_no")
    private String customerRefNo;

    @Column(name = "fiscal_year_id", nullable = false)
    private String fiscalYearId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_year_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"salesInvoices", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private FiscalYear fiscalYear;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"salesInvoices", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User user;

    @OneToMany(mappedBy = "salesInvoice", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"salesInvoice"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<SalesInvoiceItem> items;

    @OneToMany(mappedBy = "salesInvoice", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"salesInvoice"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<CogsBackfillQueue> cogsBackfills;

    @OneToMany(mappedBy = "salesInvoice", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"salesInvoice"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<CustomerDepositApplication> depositApplications;

    /** Sisi balik relasi 1:1 (Quotation.salesInvoice). Fetch/JOIN FETCH lewat field ini; JSON memakai quotation. */
    @OneToMany(mappedBy = "salesInvoice", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Quotation> quotationRef;

    @OneToMany(mappedBy = "invoice", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"invoice"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<DeliveryOrder> deliveryOrders;

    @OneToMany(mappedBy = "salesInvoice", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"salesInvoice"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<SalesReturn> returns;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "is_dummy", nullable = false)
    private Boolean isDummy = false;

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

    /** Quotation terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("quotation")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"salesInvoice", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public Quotation getQuotation() {
        if (quotationRef == null || !Hibernate.isInitialized(quotationRef) || quotationRef.isEmpty()) return null;
        return quotationRef.get(0);
    }

}
