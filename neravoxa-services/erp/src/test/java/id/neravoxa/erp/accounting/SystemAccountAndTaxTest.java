package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** /api/erp/system-accounts (pemetaan + invalidasi cache) dan /api/erp/tax (konfigurasi + laporan PPN). */
class SystemAccountAndTaxTest extends AccountingTestSupport {

    @Autowired
    ErpSystemAccountService systemAccounts;

    @Test
    void system_account_overview_and_update() throws Exception {
        asAdmin();
        JsonNode overview = ok(get("/api/erp/system-accounts"));
        assertEquals(SystemAccountRole.values().length, overview.get("roles").size());
        assertEquals("CASH", overview.get("roles").get(0).get("key").asText());
        assertEquals("Kas & Bank", overview.get("roles").get(0).get("label").asText());
        JsonNode cash = overview.get("mappings").get("CASH");
        assertEquals("1.1.1", cash.get(0).get("accountNumber").asText());
        assertTrue(cash.get(0).has("accountName"));

        Result empty = call(put("/api/erp/system-accounts", Map.of()));
        assertEquals(400, empty.status());
        assertEquals("Data tidak valid.", empty.error());
        assertEquals("Tidak ada perubahan untuk disimpan.", empty.data().get("details").get("_errors").get(0).asText());

        Result badUuid = call(put("/api/erp/system-accounts",
                Map.of("mappings", List.of(Map.of("role", "OTHER_INCOME", "accountId", "abc")))));
        assertEquals("Invalid UUID", badUuid.data().get("details").get("mappings").get("0").get("accountId")
                .get("_errors").get(0).asText());

        assertEquals("Role \"Piutang Usaha\" wajib dan tidak dapat dilepas. Ganti ke akun lain, bukan dikosongkan.",
                call(put("/api/erp/system-accounts", Map.of("clearRoles", List.of("AR")))).error());
        assertEquals("Role \"NOPE\" tidak valid.", call(put("/api/erp/system-accounts",
                Map.of("mappings", List.of(Map.of("role", "NOPE", "accountId", UUID.randomUUID().toString()))))).error());

        String accA = newAccount("7", "REVENUE").get("id").asText();
        String accB = newAccount("7", "REVENUE").get("id").asText();
        assertEquals("Role \"Pendapatan Lain-lain\" hanya boleh memiliki 1 akun, tapi diberikan 2.",
                call(put("/api/erp/system-accounts", Map.of("mappings", List.of(
                        Map.of("role", "OTHER_INCOME", "accountId", accA),
                        Map.of("role", "OTHER_INCOME", "accountId", accB))))).error());
        String missing = UUID.randomUUID().toString();
        assertEquals("Akun dengan ID \"" + missing + "\" tidak ditemukan.", call(put("/api/erp/system-accounts",
                Map.of("mappings", List.of(Map.of("role", "OTHER_INCOME", "accountId", missing))))).error());

        List<String> original = systemAccounts.getAccountIds(SystemAccountRole.OTHER_INCOME);
        try {
            JsonNode res = ok(put("/api/erp/system-accounts",
                    Map.of("mappings", List.of(Map.of("role", "OTHER_INCOME", "accountId", accA, "sortOrder", 0)))));
            assertEquals("Konfigurasi akun sistem diperbarui.", res.get("message").asText());
            assertEquals(accA, res.get("updated").get(0).get("accountId").asText());
            // Cache kernel di-invalidate setelah commit.
            assertEquals(accA, systemAccounts.getAccountId(SystemAccountRole.OTHER_INCOME));

            // Akun yang dipetakan tidak bisa dihapus.
            assertTrue(call(delete("/api/erp/coa/" + accA)).error().contains("Akun Sistem (OTHER_INCOME)"));

            JsonNode cleared = ok(put("/api/erp/system-accounts", Map.of("clearRoles", List.of("OTHER_INCOME"))));
            assertEquals(0, cleared.get("updated").size());
            assertTrue(systemAccounts.getAccounts(SystemAccountRole.OTHER_INCOME).isEmpty());
        } finally {
            if (!original.isEmpty()) {
                ok(put("/api/erp/system-accounts",
                        Map.of("mappings", List.of(Map.of("role", "OTHER_INCOME", "accountId", original.get(0))))));
            }
        }

        // Bukan Admin (punya edit settings) → 403 dengan pesan khusus.
        asUser("editor." + uniq() + "@test.local", customRole(Map.of("settings", "edit")));
        Result forbidden = call(put("/api/erp/system-accounts", Map.of("clearRoles", List.of("OTHER_INCOME"))));
        assertEquals(403, forbidden.status());
        assertEquals("Hanya Admin yang dapat mengubah konfigurasi akun sistem.", forbidden.error());
    }

    @Test
    void tax_config_crud() throws Exception {
        asAdmin();
        String name = "PPN Uji " + uniq();
        Result created = call(post("/api/erp/tax/config", Map.of("name", name, "rate", "11", "type", "sales")));
        assertEquals(201, created.status());
        String id = created.data().get("id").asText();
        assertEquals(11, created.data().get("rate").asInt());
        assertTrue(created.data().get("accountId").isNull());

        assertEquals("Tarif maksimal 100%.", call(post("/api/erp/tax/config",
                Map.of("name", "x", "rate", 150, "type", "sales"))).error());
        assertEquals("Tarif tidak boleh negatif.", call(post("/api/erp/tax/config",
                Map.of("name", "x", "rate", -1, "type", "sales"))).error());
        assertEquals("Tipe harus: sales, purchase, atau withholding.", call(post("/api/erp/tax/config",
                Map.of("name", "x", "rate", 1, "type", "vat"))).error());
        assertEquals(ZodLite.NAN, call(post("/api/erp/tax/config", Map.of("name", "x", "type", "sales"))).error());

        Map<String, Object> upd = new HashMap<>();
        upd.put("rate", 12);
        upd.put("accountId", accountId("2.2.1"));
        JsonNode updated = ok(put("/api/erp/tax/config/" + id, upd));
        assertEquals(12, updated.get("rate").asInt());
        assertEquals(name, updated.get("name").asText());
        Map<String, Object> clear = new HashMap<>();
        clear.put("accountId", null);
        assertTrue(ok(put("/api/erp/tax/config/" + id, clear)).get("accountId").isNull());
        assertEquals("Konfigurasi pajak tidak ditemukan.", call(put("/api/erp/tax/config/nope", Map.of("rate", 1))).error());

        // Hapus (nonaktif) butuh doc.cancel.
        asUser("pajak." + uniq() + "@test.local", customRole(Map.of("settings", "edit")));
        assertEquals(403, call(delete("/api/erp/tax/config/" + id)).status());
        asAdmin();
        assertEquals("Konfigurasi pajak dinonaktifkan.", ok(delete("/api/erp/tax/config/" + id)).get("message").asText());
        JsonNode list = ok(get("/api/erp/tax/config"));
        boolean found = false;
        for (JsonNode t : list) {
            if (id.equals(t.get("id").asText())) {
                found = true;
                assertFalse(t.get("isActive").asBoolean());
            }
        }
        assertTrue(found);
    }

    @Test
    void tax_report_groups_ppn_by_month() throws Exception {
        String userId = adminUserId();
        String fy = currentFiscalYearId();
        String customer = newParty("Customer");
        String supplier = newParty("Supplier");
        int year = 1901 + Math.abs(uniq().hashCode() % 90);
        String month = year + "-03";

        // Penjualan: pajak level faktur 11% dari grand total 1.110.000 → 110.000.
        jdbc.update("INSERT INTO sales_invoices (id, invoice_number, date, party_id, status, grand_total, outstanding, "
                        + "tax_pct, fiscal_year_id, created_by) VALUES (?, ?, ?::timestamp, ?, 'Submitted', 1110000, 1110000, 11, ?, ?)",
                UUID.randomUUID().toString(), "SI-TX-" + uniq(), month + "-10", customer, fy, userId);
        // Faktur Draft tidak dihitung.
        jdbc.update("INSERT INTO sales_invoices (id, invoice_number, date, party_id, status, grand_total, outstanding, "
                        + "tax_pct, fiscal_year_id, created_by) VALUES (?, ?, ?::timestamp, ?, 'Draft', 999000, 0, 11, ?, ?)",
                UUID.randomUUID().toString(), "SI-TX-" + uniq(), month + "-11", customer, fy, userId);
        // Pembelian: pajak per item 11% dari 200.000 → 22.000.
        String pi = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO purchase_invoices (id, invoice_number, date, party_id, status, grand_total, outstanding, "
                        + "fiscal_year_id, created_by) VALUES (?, ?, ?::timestamp, ?, 'Paid', 222000, 0, ?, ?)",
                pi, "PI-TX-" + uniq(), month + "-31", supplier, fy, userId);
        jdbc.update("INSERT INTO purchase_invoice_items (id, purchase_invoice_id, item_name, quantity, unit, rate, amount, "
                        + "tax_pct, account_id) VALUES (?, ?, 'Barang', 1, 'pcs', 200000, 200000, 11, ?)",
                UUID.randomUUID().toString(), pi, accountId("1.1.1"));

        asAdmin();
        assertEquals("Parameter startDate dan endDate wajib diisi.",
                call(get("/api/erp/tax/report?startDate=" + month + "-01")).error());
        JsonNode report = ok(get("/api/erp/tax/report?startDate=" + month + "-01&endDate=" + month + "-31"));
        assertEquals(1, report.get("months").size());
        JsonNode m = report.get("months").get(0);
        assertEquals(month, m.get("month").asText());
        assertEquals(110000, m.get("ppnKeluaran").asDouble());
        assertEquals(22000, m.get("ppnMasukan").asDouble());
        assertEquals(0, m.get("pph").asDouble());
        assertEquals(88000, m.get("net").asDouble());
        assertEquals(88000, report.get("totals").get("net").asDouble());
    }
}
