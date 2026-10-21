package id.enterpriseapps.erp.report;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.entity.JournalEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pencarian global (izin per jenis record, `_` harfiah) + laporan operasional (HPP, profitabilitas, harian). */
class GlobalSearchAndOperationalTest extends ReportTestSupport {

    private static boolean has(JsonNode results, String type, String title) {
        for (JsonNode r : results) {
            if (type.equals(r.get("type").asText()) && title.equals(r.get("title").asText())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void search_respects_module_permissions_and_literal_underscore() throws Exception {
        String token = uniq();
        newParty("Cari_" + token, "Customer");
        newParty("CariX" + token, "Supplier");
        JournalEntry je = post("2046-01-05", "Narasi cari " + token, "1.1.2", 1000, 0, "4.1", 0, 1000);

        asAdmin();
        JsonNode all = ok(get("/api/erp/search?q=" + token + "&limit=20"));
        assertTrue(has(all, "party", "Cari_" + token), all.toString());
        assertTrue(has(all, "journal", je.getEntryNumber()), all.toString());
        for (JsonNode r : all) {
            if ("journal".equals(r.get("type").asText())) {
                assertEquals("/gl", r.get("url").asText());
                assertEquals("Narasi cari " + token, r.get("subtitle").asText());
            }
            if ("party".equals(r.get("type").asText()) && ("CariX" + token).equals(r.get("title").asText())) {
                assertEquals("Vendor", r.get("subtitle").asText());
            }
        }

        // "_" dicari harfiah: hanya "Cari_<token>", bukan "CariX<token>".
        JsonNode literal = ok(get("/api/erp/search?q=i_" + token));
        assertTrue(has(literal, "party", "Cari_" + token));
        assertFalse(has(literal, "party", "CariX" + token), literal.toString());

        // Role produksi tidak punya izin jurnal: jurnal disembunyikan, mitra tetap tampil.
        asUser("produksi.cari@test.local", "ERP_PRODUCTION");
        JsonNode limited = ok(get("/api/erp/search?q=" + token + "&limit=20"));
        assertTrue(has(limited, "party", "Cari_" + token));
        assertFalse(has(limited, "journal", je.getEntryNumber()), limited.toString());

        assertEquals(0, ok(get("/api/erp/search?q=a")).size(), "minimal 2 karakter");
        assertEquals(1, ok(get("/api/erp/search?q=" + token + "&limit=1")).size());
    }

    @Test
    void hpp_profitability_and_daily_reports() throws Exception {
        String userId = adminUserId();
        String fy = fiscalYear(2047);
        String item = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO inventory_items (id, code, name, unit, current_stock, minimum_stock, average_cost, is_active, "
                + "created_at, updated_at) VALUES (?, ?, 'Beras HPP', 'kg', 0, 0, 6000, true, now(), now())", item, "HPP-" + uniq());
        String party = newParty("Pelanggan HPP " + uniq(), "Customer");
        String inv = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO sales_invoices (id, invoice_number, date, party_id, status, grand_total, outstanding, "
                        + "fiscal_year_id, created_by, created_at, updated_at) VALUES (?, ?, CAST('2047-02-10 00:00:00' AS timestamp), "
                        + "?, 'Submitted', 100000, 100000, ?, ?, now(), now())",
                inv, "SI-HPP-" + uniq(), party, fy, userId);
        jdbc.update("INSERT INTO sales_invoice_items (id, sales_invoice_id, inventory_item_id, item_name, quantity, unit, rate, "
                        + "amount, account_id, unit_cost_loaded) VALUES (?, ?, ?, 'Beras HPP', 10, 'kg', 10000, 100000, ?, 7000)",
                UUID.randomUUID().toString(), inv, item, accountId("4.1"));
        asAdmin();

        JsonNode hpp = ok(get("/api/erp/reports/hpp?startDate=2047-01-01&endDate=2047-12-31"));
        assertEquals(1, hpp.get("items").size());
        JsonNode line = hpp.get("items").get(0);
        assertEquals(0, new BigDecimal("100000").compareTo(line.get("totalRevenue").decimalValue()));
        assertEquals(0, new BigDecimal("60000").compareTo(line.get("totalCogs").decimalValue()));
        assertEquals(0, new BigDecimal("40.0").compareTo(line.get("marginPct").decimalValue()));
        assertEquals(0, new BigDecimal("40000").compareTo(hpp.get("summary").get("grossProfit").decimalValue()));
        assertEquals("2047-01-01T00:00:00.000Z", hpp.get("period").get("start").asText());

        JsonNode sku = ok(get("/api/erp/reports/profitability-per-sku?startDate=2047-01-01&endDate=2047-12-31"));
        JsonNode s = sku.get("items").get(0);
        assertEquals(0, new BigDecimal("70000").compareTo(s.get("loadedCogs").decimalValue()));
        assertEquals(0, new BigDecimal("30.0").compareTo(s.get("loadedMarginPct").decimalValue()));
        assertEquals(1, s.get("linesWithBatch").asInt());
        assertEquals(0, new BigDecimal("100").compareTo(s.get("coveragePct").decimalValue()));

        JsonNode cost = ok(get("/api/erp/reports/production-cost-summary?startDate=2047-01-01&endDate=2047-12-31"));
        assertEquals("TOTAL", cost.get("total").get("outputName").asText());
        assertEquals(0, cost.get("runs").size());

        JsonNode daily = ok(get("/api/erp/reports/daily?date=2047-02-10"));
        assertEquals("2047-02-10", daily.get("date").asText());
        assertEquals(1, daily.get("sales").get("summary").get("count").asInt());
        JsonNode first = daily.get("sales").get("invoices").get(0);
        assertEquals("Beras HPP", first.get("items").get(0).get("itemName").asText());
        assertEquals(0, new BigDecimal("10").compareTo(first.get("totalQty").decimalValue()));
        for (String k : new String[]{"purchases", "production", "finance", "receivables", "payables", "vendorDeposits", "stock"}) {
            assertTrue(daily.has(k), k);
        }
        assertEquals("2047-02-10", daily.get("stock").get("asOfDate").asText());
        assertTrue(daily.get("finance").has("cashBankBalances"));
        assertEquals(400, call(get("/api/erp/reports/daily?date=bukan")).status());
    }
}
