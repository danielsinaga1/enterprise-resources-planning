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

/** Tabel quotations. (Dibangkitkan dari model Prisma Quotation.) */
@Entity
@Table(name = "quotations")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Quotation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "quotation_number", nullable = false)
    private String quotationNumber;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(name = "party_id", nullable = false)
    private String partyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"quotations", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Party customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private QuotationStatus status = QuotationStatus.Draft;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

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

    @Column(name = "grand_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "terms")
    private String terms;

    @Column(name = "notes")
    private String notes;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "sales_invoice_id")
    private String salesInvoiceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_invoice_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"quotation", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private SalesInvoice salesInvoice;

    @OneToMany(mappedBy = "quotation", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"quotation"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<SalesOrder> salesOrders;

    /** Sisi balik relasi 1:1 (Inquiry.quotation). Fetch/JOIN FETCH lewat field ini; JSON memakai inquiry. */
    @OneToMany(mappedBy = "quotation", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Inquiry> inquiryRef;

    @Column(name = "fiscal_year_id", nullable = false)
    private String fiscalYearId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_year_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"quotations", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private FiscalYear fiscalYear;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"quotations", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User user;

    @OneToMany(mappedBy = "quotation", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"quotation"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<QuotationItem> items;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "expiry_notified_at")
    private Instant expiryNotifiedAt;

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

    /** Inquiry terkait (relasi 1:1), null bila tidak ada atau belum di-fetch. */
    @JsonProperty("inquiry")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(value = {"quotation", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    public Inquiry getInquiry() {
        if (inquiryRef == null || !Hibernate.isInitialized(inquiryRef) || inquiryRef.isEmpty()) return null;
        return inquiryRef.get(0);
    }

}
