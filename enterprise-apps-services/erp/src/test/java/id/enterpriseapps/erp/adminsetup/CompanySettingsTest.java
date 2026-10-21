package id.enterpriseapps.erp.adminsetup;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.storage.ErpFileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** /api/erp/settings: profil perusahaan (GET terbuka, PUT butuh edit settings), logo publik, onboarding. */
class CompanySettingsTest extends AdminSetupTestSupport {

    private static final String DENIED = "Akses ditolak: Tidak memiliki izin.";
    private static final String URL = "/api/erp/settings/company";

    @Autowired
    ErpFileStorage storage;

    private String viewer() throws Exception {
        String email = uniq("setviewer").toLowerCase() + "@corp.test";
        platformUser("Viewer Setting", null, email, null, true, "ERP_VIEWER");
        return login(email, "ERP_VIEWER");
    }

    private static Map<String, Object> company(String name) {
        Map<String, Object> b = new HashMap<>();
        b.put("companyName", name);
        b.put("address", "Jl. Uji No. 1");
        b.put("phone", "021-555");
        b.put("email", "finance@contoh.co.id");
        b.put("taxId", "01.234.567.8-901.000");
        b.put("defaultCurrency", "IDR");
        b.put("defaultLanguage", "id");
        return b;
    }

    private Path file(String url) {
        return storage.resolvePublic(url.substring("/uploads/".length()));
    }

    private static String dataUrl(String mime, byte[] bytes) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void get_open_put_requires_settings_edit_and_follows_source_semantics() throws Exception {
        viewer();
        JsonNode open = ok(get(URL));
        assertTrue(open.has("companyName") && open.has("logoUrl") && open.has("invoiceSettings"));
        assertTrue(open.get("hasDummyData").isBoolean());
        assertError(call(put(URL, company("PT Tidak Boleh"))), 403, DENIED);
        assertError(call(post(URL + "/onboarding", Map.of())), 403, DENIED);

        adminId();
        String name = uniq("PT Uji");
        Map<String, Object> body = company(name);
        body.put("phone", "");
        body.put("email", "");
        body.put("taxId", null);
        body.put("fiscalYearStartMonth", "4");
        body.put("defaultLanguage", "en");
        Map<String, Object> sales = new HashMap<>();
        sales.put("bankAccounts", "BCA 123");
        sales.put("footerNote", "Terima kasih");
        sales.put("showSignature", true);
        sales.put("signatureLabels", List.of("Dibuat", "Disetujui"));
        sales.put("extra", 1);
        body.put("invoiceSettings", Map.of("sales", sales, "purchase", Map.of("footerNote", "Catatan PO"), "junk", true));
        JsonNode saved = ok(put(URL, body));
        assertEquals(name, saved.get("companyName").asText());
        assertEquals("Jl. Uji No. 1", saved.get("address").asText());
        assertTrue(saved.get("phone").isNull());
        assertTrue(saved.get("email").isNull());
        assertTrue(saved.get("taxId").isNull());
        assertEquals(4, saved.get("fiscalYearStartMonth").asInt());
        assertEquals("en", saved.get("defaultLanguage").asText());
        assertEquals("default", saved.get("slug").asText());
        JsonNode inv = saved.get("invoiceSettings");
        assertEquals("BCA 123", inv.get("sales").get("bankAccounts").asText());
        assertEquals(2, inv.get("sales").get("signatureLabels").size());
        assertFalse(inv.get("sales").has("extra"));
        assertFalse(inv.has("junk"));
        assertEquals("Catatan PO", inv.get("purchase").get("footerNote").asText());
        assertFalse(saved.has("hasDummyData"));

        JsonNode read = ok(get(URL));
        assertEquals(name, read.get("companyName").asText());
        assertEquals("en", read.get("defaultLanguage").asText());
        assertTrue(read.has("hasDummyData"));

        // Alias "name"; field identitas yang tidak dikirim di-default; bahasa & format invoice dipertahankan.
        String alias = uniq("PT Alias");
        JsonNode aliased = ok(put(URL, Map.of("name", alias)));
        assertEquals(alias, aliased.get("companyName").asText());
        assertTrue(aliased.get("address").isNull());
        assertEquals("IDR", aliased.get("defaultCurrency").asText());
        assertEquals(1, aliased.get("fiscalYearStartMonth").asInt());
        assertEquals("en", aliased.get("defaultLanguage").asText());
        assertEquals("BCA 123", aliased.get("invoiceSettings").get("sales").get("bankAccounts").asText());
        assertEquals("Perusahaan Anda", ok(put(URL, Map.of("currency", "USD"))).get("companyName").asText());

        // companyName "" jatuh ke `|| name` (undefined) → lolos dan menjadi default; error hanya bila keduanya kosong.
        assertEquals("Perusahaan Anda", ok(put(URL, Map.of("companyName", ""))).get("companyName").asText());
        assertError(call(put(URL, Map.of("companyName", "", "name", ""))), 400, "Nama perusahaan wajib diisi.");
        assertError(call(put(URL, Map.of("companyName", "PT A", "email", "bukan-email"))), 400, "Format email tidak valid.");
        assertError(call(put(URL, Map.of("companyName", "PT A", "fiscalYearStartMonth", 13))), 400, "Bulan harus antara 1-12.");
        assertError(call(put(URL, Map.of("companyName", "PT A", "fiscalYearStartMonth", "abc"))), 400,
                "Invalid input: expected number, received NaN");
        assertError(call(put(URL, Map.of("companyName", "PT A", "fiscalYearStartMonth", 2.5))), 400,
                "Invalid input: expected int, received number");
        assertError(call(put(URL, Map.of("companyName", "PT A", "defaultLanguage", "fr"))), 400, "Bahasa harus id atau en.");
        assertError(call(put(URL, Map.of("companyName", "PT A", "invoiceSettings", Map.of("sales", Map.of("showSignature", "ya"))))),
                400, "Invalid input: expected boolean, received string");
        assertError(call(put(URL, Map.of("companyName", "PT A", "logoUrl", "x".repeat(5 * 1024 * 1024 + 1)))), 400,
                "Ukuran logo terlalu besar (maks. ~4MB setelah encoding).");

        Map<String, Object> clearInvoice = company(uniq("PT Akhir"));
        clearInvoice.put("invoiceSettings", null);
        assertTrue(ok(put(URL, clearInvoice)).get("invoiceSettings").isNull());
        assertEquals("id", jdbc.queryForObject("SELECT default_language FROM company_settings WHERE slug = 'default'", String.class));
    }

    @Test
    void raster_logo_is_stored_as_public_file_and_cleaned_up() throws Exception {
        adminId();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0x0d, 'I', 'H', 'D', 'R', 1, 2, 3, 4};
        byte[] jpg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
        String svg = dataUrl("image/svg+xml", "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> body = company(uniq("PT Logo"));
        body.put("logoUrl", dataUrl("image/png", png));
        body.put("logoUrlDark", svg);
        JsonNode s1 = ok(put(URL, body));
        String logo = s1.get("logoUrl").asText();
        assertTrue(logo.matches("^/uploads/erp/settings/logo/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png$"), logo);
        Path pngFile = file(logo);
        assertArrayEquals(png, Files.readAllBytes(pngFile));
        assertEquals(svg, s1.get("logoUrlDark").asText(), "SVG tetap data URL (tidak ditulis ke folder publik)");
        assertEquals(logo, ok(get(URL)).get("logoUrl").asText());

        // URL yang dikirim balik apa adanya → tidak ada berkas baru, berkas lama tetap.
        body.put("logoUrl", logo);
        assertEquals(logo, ok(put(URL, body)).get("logoUrl").asText());
        assertTrue(Files.exists(pngFile));

        // Field logo tidak dikirim → tidak berubah.
        Map<String, Object> noLogo = company(uniq("PT Logo"));
        assertEquals(logo, ok(put(URL, noLogo)).get("logoUrl").asText());

        // Ganti logo → berkas lama dihapus setelah commit.
        body.put("logoUrl", dataUrl("image/jpeg", jpg));
        String logo2 = ok(put(URL, body)).get("logoUrl").asText();
        assertTrue(logo2.endsWith(".jpg"), logo2);
        assertFalse(Files.exists(pngFile));
        Path jpgFile = file(logo2);
        assertArrayEquals(jpg, Files.readAllBytes(jpgFile));

        // URL eksternal disimpan apa adanya; logo dihapus → null + berkas dihapus.
        body.put("logoUrl", "https://cdn.contoh.co.id/logo.png");
        assertEquals("https://cdn.contoh.co.id/logo.png", ok(put(URL, body)).get("logoUrl").asText());
        assertFalse(Files.exists(jpgFile));
        body.put("logoUrl", "");
        body.put("logoUrlDark", null);
        JsonNode cleared = ok(put(URL, body));
        assertTrue(cleared.get("logoUrl").isNull());
        assertTrue(cleared.get("logoUrlDark").isNull());
    }

    @Test
    void onboarding_is_idempotent() throws Exception {
        adminId();
        JsonNode first = ok(post(URL + "/onboarding", Map.of()));
        assertEquals(2, first.size());
        assertTrue(first.hasNonNull("id"));
        String at = first.get("onboardedAt").asText();
        assertTrue(at.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$"), at);
        JsonNode second = ok(post(URL + "/onboarding", Map.of()));
        assertEquals(first.get("id").asText(), second.get("id").asText());
        assertEquals(at, second.get("onboardedAt").asText());
    }
}
