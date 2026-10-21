package id.enterpriseapps.erp.entity;

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

/** Tabel project_guarantees. (Dibangkitkan dari model Prisma ProjectGuarantee.) */
@Entity
@Table(name = "project_guarantees")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class ProjectGuarantee {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "guarantee_number", nullable = false)
    private String guaranteeNumber;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Column(name = "party_id", nullable = false)
    private String partyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", insertable = false, updatable = false)
    @JsonIgnoreProperties(value = {"projectGuarantees", "hibernateLazyInitializer", "handler"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private Party party;

    @Column(name = "guarantee_type", nullable = false)
    private String guaranteeType;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Column(name = "reference_no")
    private String referenceNo;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "expected_return_date")
    private Instant expectedReturnDate;

    @Column(name = "notes")
    private String notes;

    @Column(name = "status", nullable = false)
    private String status = "Disetor";

    @Column(name = "journal_entry_id")
    private String journalEntryId;

    @Column(name = "fiscal_year_id", nullable = false)
    private String fiscalYearId;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @OneToMany(mappedBy = "guarantee", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"guarantee"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<ProjectGuaranteeSettlement> settlements;

    @Column(name = "submitted_at")
    private Instant submittedAt;

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

}
