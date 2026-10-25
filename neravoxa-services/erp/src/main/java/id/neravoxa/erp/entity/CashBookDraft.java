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

/** Draft Buku Kas (dari nota / halaman buku kas). Tidak berefek ke GL sampai diposting. (Dibangkitkan dari model Prisma CashBookDraft.) */
@Entity
@Table(name = "cash_book_drafts")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class CashBookDraft {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "draft_number", nullable = false)
    private String draftNumber;

    @Column(name = "status", nullable = false)
    private String status = "Draft";

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "entry_date", nullable = false)
    private Instant entryDate;

    @Column(name = "cash_account_id", nullable = false)
    private String cashAccountId;

    @Column(name = "opening_balance", precision = 20, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 20, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "source", nullable = false)
    private String source = "manual";

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "source_note")
    private String sourceNote;

    @Column(name = "created_by_id", nullable = false)
    private String createdById;

    @Column(name = "created_via")
    private String createdVia;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by_id")
    private String postedById;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "draft", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"draft"}, allowSetters = true)
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = LazyNotLoadedFilter.class)
    private List<CashBookDraftRow> rows;

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
