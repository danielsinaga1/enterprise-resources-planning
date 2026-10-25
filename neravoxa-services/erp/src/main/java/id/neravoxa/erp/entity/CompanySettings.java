package id.neravoxa.erp.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tabel company_settings. (Dibangkitkan dari model Prisma CompanySettings.) */
@Entity
@Table(name = "company_settings")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class CompanySettings {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "slug", nullable = false)
    private String slug = "default";

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "address")
    private String address;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "logo_url_dark")
    private String logoUrlDark;

    @Column(name = "default_currency", nullable = false)
    private String defaultCurrency = "IDR";

    @Column(name = "fiscal_year_start_month", nullable = false)
    private Integer fiscalYearStartMonth = 1;

    /** Default UI language for users without their own choice: 'id' | 'en'. */
    @Column(name = "default_language", nullable = false)
    private String defaultLanguage = "id";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "invoice_settings")
    private JsonNode invoiceSettings;

    /** Penyiapan awal (onboarding wizard) selesai/dilewati admin. NULL = belum. */
    @Column(name = "onboarded_at")
    private Instant onboardedAt;

    @PrePersist
    protected void onPrePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
    }

}
