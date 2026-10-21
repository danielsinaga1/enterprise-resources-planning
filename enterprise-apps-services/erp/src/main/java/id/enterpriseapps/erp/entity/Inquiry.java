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

/** Tabel inquiries. (Dibangkitkan dari model Prisma Inquiry.) */
@Entity
@Table(name = "inquiries")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Inquiry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "inquiry_number", nullable = false)
    private String inquiryNumber;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InquiryStatus status = InquiryStatus.Baru;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private InquiryChannel channel = InquiryChannel.Lainnya;

    @Column(name = "party_id")
    private String partyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"inquiries", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Party party;

    @Column(name = "prospect_name", nullable = false)
    private String prospectName;

    @Column(name = "prospect_phone")
    private String prospectPhone;

    @Column(name = "prospect_email")
    private String prospectEmail;

    @Column(name = "items_mode", nullable = false)
    private String itemsMode = "freetext";

    @Column(name = "items_text")
    private String itemsText;

    @OneToMany(mappedBy = "inquiry", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"inquiry"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<InquiryItem> items;

    @Column(name = "notes")
    private String notes;

    @Column(name = "follow_up_date")
    private Instant followUpDate;

    @Column(name = "loss_reason")
    private String lossReason;

    @Column(name = "quotation_id")
    private String quotationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"inquiry", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Quotation quotation;

    @Column(name = "converted_at")
    private Instant convertedAt;

    @Column(name = "fiscal_year_id", nullable = false)
    private String fiscalYearId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_year_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"inquiries", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private FiscalYear fiscalYear;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"inquiries", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private User user;

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
