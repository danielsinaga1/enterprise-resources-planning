package id.enterpriseapps.erp.adminsetup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.CompanySettings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pengaturan perusahaan — port bagian non-demo {@code routes/settings.ts}: profil perusahaan
 * (GET/PUT, logo terang/gelap, bahasa default, format invoice) dan status penyiapan awal.
 * Tidak dipindahkan: data dummy/demo, info runtime, cek update versi.
 */
@Service
public class ErpCompanySettingsService {

    static final int MAX_LOGO_CHARS = 5 * 1024 * 1024;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private final ErpCompanyLogoStore logos;

    public ErpCompanySettingsService(ErpCompanyLogoStore logos) {
        this.logos = logos;
    }

    private CompanySettings findDefault() {
        List<CompanySettings> rows = em.createQuery(
                "select c from CompanySettings c where c.slug = 'default'", CompanySettings.class).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** GET /settings/company → {@code { ...settings, hasDummyData }} atau null bila belum ada. */
    @ErpTx(readOnly = true)
    public JsonNode company() {
        CompanySettings s = findDefault();
        if (s == null) {
            return NullNode.getInstance();
        }
        // Tabel batch demo tidak dipindahkan; data dummy lama hanya bisa berupa mitra is_dummy.
        boolean dummy = !em.createQuery("select p.id from Party p where p.isDummy = true", String.class)
                .setMaxResults(1).getResultList().isEmpty();
        return ErpJson.merge(s, ErpJson.map("hasDummyData", dummy));
    }

    /**
     * PUT /settings/company — semantik sumber dipertahankan: field identitas yang tidak dikirim
     * dikosongkan/di-default (nama → "Perusahaan Anda", mata uang → IDR, bulan awal fiskal → 1),
     * sedangkan bahasa, logo, dan format invoice hanya diubah bila dikirim.
     */
    @ErpTx
    public CompanySettings update(JsonNode raw) {
        JsonNode body = AdminZod.body(raw);
        // mergedBody: companyName = companyName || name; currency = defaultCurrency || currency.
        JsonNode companyName = AdminZod.truthy(body.get("companyName"))
                ? body.get("companyName") : AdminZod.field(body, "name");
        JsonNode currency = AdminZod.truthy(body.get("defaultCurrency"))
                ? body.get("defaultCurrency") : AdminZod.field(body, "currency");
        JsonNode address = AdminZod.field(body, "address");
        JsonNode phone = AdminZod.field(body, "phone");
        JsonNode email = AdminZod.field(body, "email");
        JsonNode taxId = AdminZod.field(body, "taxId");
        JsonNode logoUrl = AdminZod.field(body, "logoUrl");
        JsonNode logoUrlDark = AdminZod.field(body, "logoUrlDark");
        JsonNode dateFormat = AdminZod.field(body, "dateFormat");
        JsonNode fiscal = AdminZod.field(body, "fiscalYearStartMonth");
        JsonNode defaultLanguage = AdminZod.field(body, "defaultLanguage");
        JsonNode invoiceSettings = AdminZod.field(body, "invoiceSettings");

        // Port UpdateCompanySettingsSchema (urutan shape).
        if (AdminZod.present(companyName)) {
            AdminZod.fail(AdminZod.optionalString(companyName));
            if (companyName.asText().isEmpty()) {
                AdminZod.fail("Nama perusahaan wajib diisi.");
            }
        }
        AdminZod.fail(AdminZod.optionalNullableString(address));
        AdminZod.fail(AdminZod.optionalNullableString(phone));
        AdminZod.fail(AdminZod.emailOrEmpty(email, "Format email tidak valid."));
        AdminZod.fail(AdminZod.optionalNullableString(taxId));
        AdminZod.fail(AdminZod.optionalNullableString(logoUrl));
        AdminZod.fail(AdminZod.optionalNullableString(logoUrlDark));
        AdminZod.fail(AdminZod.optionalString(currency));
        AdminZod.fail(AdminZod.optionalString(dateFormat));
        Integer fiscalMonth = fiscalMonth(fiscal);
        if (AdminZod.present(defaultLanguage) && !AdminZod.isLanguage(defaultLanguage)) {
            AdminZod.fail(AdminZod.LANGUAGE_MSG);
        }
        JsonNode invoice = AdminZod.present(invoiceSettings) ? invoiceSettings(invoiceSettings) : null;

        String logo = AdminZod.text(logoUrl);
        if (logo != null && logo.length() > MAX_LOGO_CHARS) {
            AdminZod.fail("Ukuran logo terlalu besar (maks. ~4MB setelah encoding).");
        }
        String logoDark = AdminZod.text(logoUrlDark);
        if (logoDark != null && logoDark.length() > MAX_LOGO_CHARS) {
            AdminZod.fail("Ukuran logo (dark mode) terlalu besar (maks. ~4MB setelah encoding).");
        }

        CompanySettings s = findDefault();
        boolean create = s == null;
        if (create) {
            s = new CompanySettings();
            s.setSlug("default");
        }
        String oldLogo = s.getLogoUrl();
        String oldLogoDark = s.getLogoUrlDark();

        String name = AdminZod.text(companyName);
        s.setCompanyName(name != null ? name : "Perusahaan Anda");
        s.setAddress(orNull(AdminZod.text(address)));
        s.setPhone(orNull(AdminZod.text(phone)));
        s.setEmail(orNull(AdminZod.text(email)));
        s.setTaxId(orNull(AdminZod.text(taxId)));
        String cur = AdminZod.text(currency);
        s.setDefaultCurrency(cur != null && !cur.isEmpty() ? cur : "IDR");
        s.setFiscalYearStartMonth(fiscalMonth != null ? fiscalMonth : 1);
        if (AdminZod.present(defaultLanguage)) {
            s.setDefaultLanguage(defaultLanguage.asText());
        }
        if (AdminZod.present(logoUrl)) {
            s.setLogoUrl(logos.store(orNull(logo)));
        }
        if (AdminZod.present(logoUrlDark)) {
            s.setLogoUrlDark(logos.store(orNull(logoDark)));
        }
        if (AdminZod.present(invoiceSettings)) {
            s.setInvoiceSettings(invoice);
        }
        if (create) {
            em.persist(s);
        }
        em.flush();
        logos.discardAfterCommit(oldLogo, s.getLogoUrl(), s.getLogoUrlDark());
        logos.discardAfterCommit(oldLogoDark, s.getLogoUrl(), s.getLogoUrlDark());
        return s;
    }

    /** POST /settings/company/onboarding — idempoten; respons minimal (tanpa logo) untuk audit. */
    @ErpTx
    public Map<String, Object> onboarding() {
        CompanySettings s = findDefault();
        if (s == null) {
            s = new CompanySettings();
            s.setSlug("default");
            s.setCompanyName("");
            s.setOnboardedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
            em.persist(s);
        } else if (s.getOnboardedAt() == null) {
            s.setOnboardedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        }
        em.flush();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", s.getId());
        out.put("onboardedAt", s.getOnboardedAt());
        return out;
    }

    /** JS {@code value || null} untuk string. */
    private static String orNull(String v) {
        return v == null || v.isEmpty() ? null : v;
    }

    /** {@code z.coerce.number().int().min(1, msg).max(12, msg).optional()}. */
    private static Integer fiscalMonth(JsonNode v) {
        if (!AdminZod.present(v)) {
            return null;
        }
        double n = AdminZod.jsNumber(v);
        if (Double.isNaN(n)) {
            AdminZod.fail("Invalid input: expected number, received NaN");
        }
        if (Double.isInfinite(n)) {
            AdminZod.fail("Invalid input: expected number, received number");
        }
        if (n != Math.rint(n)) {
            AdminZod.fail("Invalid input: expected int, received number");
        }
        if (n < 1 || n > 12) {
            AdminZod.fail("Bulan harus antara 1-12.");
        }
        return (int) n;
    }

    /**
     * Validasi + pembersihan {@code invoiceSettings} (objek Zod membuang key yang tidak dikenal).
     * null = hapus pengaturan (Prisma.DbNull).
     */
    private static JsonNode invoiceSettings(JsonNode v) {
        if (v.isNull()) {
            return null;
        }
        if (!v.isObject()) {
            AdminZod.fail(AdminZod.expected("object", v));
        }
        ObjectNode out = ErpJson.object();
        JsonNode sales = AdminZod.field(v, "sales");
        if (AdminZod.present(sales)) {
            out.set("sales", invoiceSection(sales, true));
        }
        JsonNode purchase = AdminZod.field(v, "purchase");
        if (AdminZod.present(purchase)) {
            out.set("purchase", invoiceSection(purchase, false));
        }
        return out;
    }

    private static ObjectNode invoiceSection(JsonNode v, boolean withBank) {
        if (!v.isObject()) {
            AdminZod.fail(AdminZod.expected("object", v));
        }
        ObjectNode out = ErpJson.object();
        if (withBank) {
            copyString(v, out, "bankAccounts");
        }
        copyString(v, out, "footerNote");
        JsonNode show = AdminZod.field(v, "showSignature");
        AdminZod.fail(AdminZod.optionalBoolean(show));
        if (AdminZod.present(show)) {
            out.set("showSignature", show);
        }
        JsonNode labels = AdminZod.field(v, "signatureLabels");
        if (AdminZod.present(labels)) {
            if (!labels.isArray()) {
                AdminZod.fail(AdminZod.expected("array", labels));
            }
            for (JsonNode l : labels) {
                if (!l.isTextual()) {
                    AdminZod.fail(AdminZod.expected("string", l));
                }
            }
            out.set("signatureLabels", labels);
        }
        return out;
    }

    private static void copyString(JsonNode from, ObjectNode to, String key) {
        JsonNode v = AdminZod.field(from, key);
        AdminZod.fail(AdminZod.optionalString(v));
        if (AdminZod.present(v)) {
            to.set(key, v);
        }
    }
}
