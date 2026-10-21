package id.enterpriseapps.erp.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Laporan keuangan dari ledger: neraca saldo, laba rugi, neraca, buku besar, arus kas, aging, ekspor. */
class FinancialReportFlowTest extends ReportTestSupport {

    private static BigDecimal num(JsonNode n) {
        return n.decimalValue();
    }

    private static void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(num(actual)), "expected " + expected + " but was " + actual);
    }

    private static JsonNode row(JsonNode rows, String field, String value) {
        for (JsonNode r : rows) {
            if (value.equals(r.path(field).asText())) {
                return r;
            }
        }
        return null;
    }

    private static JsonNode findAccount(JsonNode items, String accountNumber) {
        for (JsonNode i : items) {
            if (accountNumber.equals(i.path("accountNumber").asText())) {
                return i;
            }
            JsonNode inChild = findAccount(i.path("children"), accountNumber);
            if (inChild != null) {
                return inChild;
            }
        }
        return null;
    }

    @Test
    void trial_balance_profit_loss_balance_sheet_and_ledger_agree() throws Exception {
        post("2041-03-10", "Penjualan uji TB", "1.1.2", 1_000_000, 0, "4.1", 0, 1_000_000);
        post("2041-04-05", "Beban uji TB", "6.1", 250_000, 0, "1.1.2", 0, 250_000);
        post("2041-05-01", "Setoran modal uji TB", "1.1.2", 5_000_000, 0, "3.1", 0, 5_000_000);
        asAdmin();

        // Neraca saldo: seimbang, sama dengan ledger per akun.
        JsonNode tb = ok(get("/api/erp/reports/trial-balance?startDate=2041-01-01&endDate=2041-12-31"));
        assertEquals(4, tb.size(), tb.toString());
        BigDecimal dr = BigDecimal.ZERO;
        BigDecimal cr = BigDecimal.ZERO;
        for (JsonNode r : tb) {
            dr = dr.add(num(r.get("debit")));
            cr = cr.add(num(r.get("credit")));
            BigDecimal[] l = ledger(r.get("accountNumber").asText(), "2041-01-01", "2042-01-01");
            assertEquals(0, l[0].compareTo(num(r.get("debit"))));
            assertEquals(0, l[1].compareTo(num(r.get("credit"))));
            assertTrue(r.has("accountType") && r.has("rootType") && r.has("id") && r.has("name"));
        }
        assertEquals(0, dr.compareTo(cr));
        assertMoney("5750000", row(tb, "accountNumber", "1.1.2").get("balance"));
        assertMoney("1000000", row(tb, "accountNumber", "4.1").get("balance"));
        assertMoney("250000", row(tb, "accountNumber", "6.1").get("balance"));
        assertEquals("1.1.2", tb.get(0).get("accountNumber").asText(), "urut nomor akun");

        // Laba rugi.
        JsonNode pl = ok(get("/api/erp/reports/profit-loss?startDate=2041-01-01&endDate=2041-12-31"));
        assertMoney("1000000", pl.get("totalRevenue"));
        assertMoney("250000", pl.get("totalExpense"));
        assertMoney("750000", pl.get("netProfit"));
        assertMoney("1000000", findAccount(pl.get("revenue"), "4.1").get("balance"));
        assertEquals("4", pl.get("revenue").get(0).get("accountNumber").asText());
        assertTrue(pl.get("revenue").get(0).get("isGroup").asBoolean());

        // Neraca: aset = kewajiban + ekuitas (laba berjalan sintetis menggantikan 3.4).
        JsonNode bs = ok(get("/api/erp/reports/balance-sheet?date=2041-12-31"));
        BigDecimal assets = num(bs.get("totalAssets"));
        BigDecimal liabilities = num(bs.get("totalLiabilities"));
        BigDecimal equity = num(bs.get("totalEquity"));
        assertEquals(0, assets.compareTo(liabilities.add(equity)), bs.toString());
        JsonNode equityItems = bs.get("equity");
        JsonNode synthetic = equityItems.get(equityItems.size() - 1);
        assertEquals("current-profit", synthetic.get("id").asText());
        assertEquals("Laba Tahun Berjalan", synthetic.get("name").asText());
        assertEquals(1, countAccount(equityItems, "3.4"), "akun 3.4 asli dibuang, hanya baris sintetis");

        // Detail buku besar: saldo berjalan arah debit untuk aset.
        String bank = accountId("1.1.2");
        JsonNode detail = ok(get("/api/erp/reports/ledger-detail?accountId=" + bank
                + "&startDate=2041-01-01&endDate=2041-12-31"));
        JsonNode entries = detail.get("entries");
        assertEquals(3, entries.size());
        assertMoney("1000000", entries.get(0).get("runningBalance"));
        assertMoney("750000", entries.get(1).get("runningBalance"));
        assertMoney("5750000", entries.get(2).get("runningBalance"));
        assertEquals("1.1.2", detail.get("accountNumber").asText());
        assertEquals("JournalEntry", entries.get(0).get("referenceType").asText());

        // Buku besar lengkap: saldo awal = semua ledger sebelum startDate.
        JsonNode book = ok(get("/api/erp/reports/ledger-book?startDate=2041-04-01&endDate=2041-12-31"));
        JsonNode bankRow = row(book, "accountNumber", "1.1.2");
        assertNotNull(bankRow);
        BigDecimal[] before = ledger("1.1.2", "1900-01-01", "2041-04-01");
        BigDecimal opening = before[0].subtract(before[1]);
        assertEquals(0, opening.compareTo(num(bankRow.get("openingBalance"))));
        assertEquals(2, bankRow.get("entries").size());
        assertEquals(0, opening.add(new BigDecimal("4750000")).compareTo(num(bankRow.get("closingBalance"))));
        assertMoney("5000000", bankRow.get("totalDebit"));
        assertMoney("250000", bankRow.get("totalCredit"));
    }

    private static int countAccount(JsonNode items, String number) {
        int n = 0;
        for (JsonNode i : items) {
            if (number.equals(i.path("accountNumber").asText())) {
                n++;
            }
            n += countAccount(i.path("children"), number);
        }
        return n;
    }

    @Test
    void cash_flow_classifies_offsetting_accounts() throws Exception {
        post("2042-02-01", "Arus kas jual", "1.1.2", 300_000, 0, "4.1", 0, 300_000);
        post("2042-02-02", "Arus kas aset", "1.6.1", 100_000, 0, "1.1.2", 0, 100_000);
        post("2042-02-03", "Arus kas modal", "1.1.2", 50_000, 0, "3.1", 0, 50_000);
        asAdmin();
        JsonNode cf = ok(get("/api/erp/reports/cash-flow?startDate=2042-01-01&endDate=2042-12-31"));
        assertMoney("300000", cf.get("operating"));
        assertMoney("-100000", cf.get("investing"));
        assertMoney("50000", cf.get("financing"));
        assertMoney("250000", cf.get("netChange"));
        assertEquals(1, cf.get("details").get("investing").size());
        assertEquals("Tanah", cf.get("details").get("investing").get(0).get("name").asText());
    }

    @Test
    void aging_buckets_and_schedules() throws Exception {
        String name = "Pelanggan Aging " + uniq();
        String party = newParty(name, "Customer");
        newInvoice(false, party, "SI-AG-" + uniq(), "Submitted", new BigDecimal("500000"), new BigDecimal("500000"), -45);
        newInvoice(false, party, "SI-AG-" + uniq(), "PartiallyPaid", new BigDecimal("300000"), new BigDecimal("200000"), 10);
        newInvoice(false, party, "SI-AG-" + uniq(), "Draft", new BigDecimal("900000"), new BigDecimal("900000"), -100);
        asAdmin();
        JsonNode aging = ok(get("/api/erp/reports/aging?type=Customer"));
        JsonNode r = row(aging, "name", name);
        assertNotNull(r, aging.toString());
        assertMoney("500000", r.get("31"));
        assertMoney("200000", r.get("current"));
        assertMoney("0", r.get("1"));
        assertMoney("700000", r.get("total"));

        String supplierName = "Vendor Aging " + uniq();
        String supplier = newParty(supplierName, "Supplier");
        newInvoice(true, supplier, "PI-AG-" + uniq(), "Submitted", new BigDecimal("80000"), new BigDecimal("80000"), -100);
        JsonNode ap = ok(get("/api/erp/reports/aging?type=Supplier"));
        assertMoney("80000", row(ap, "name", supplierName).get("91"));

        JsonNode receivable = ok(get("/api/erp/reports/receivable-schedule"));
        JsonNode overdue = null;
        for (JsonNode inv : receivable.get("invoices")) {
            if (name.equals(inv.get("customerName").asText()) && "overdue".equals(inv.get("status").asText())) {
                overdue = inv;
            }
        }
        assertNotNull(overdue);
        assertTrue(overdue.get("daysUntilDue").asLong() <= -44);
        assertTrue(receivable.get("summary").has("overdueAmount"));
        JsonNode payable = ok(get("/api/erp/reports/payable-schedule"));
        assertTrue(payable.get("summary").has("dueSoonAmount"));
    }

    @Test
    void export_returns_valid_xlsx() throws Exception {
        asAdmin();
        List<Map<String, Object>> data = new ArrayList<>();
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("Akun", "1.1.2");
        a.put("Debit", 100.5);
        a.put("Aktif", true);
        data.add(a);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("Akun", "4.1");
        b.put("Debit", 0);
        b.put("Catatan", "abaikan");
        data.add(b);
        MockHttpServletResponse res = raw(post("/api/erp/reports/export",
                Map.of("filename", "Neraca Saldo 2041", "data", data)));
        assertEquals(200, res.getStatus());
        assertEquals(ReportExcel.CONTENT_TYPE, res.getContentType());
        assertEquals("attachment; filename=\"Neraca_Saldo_2041.xlsx\"", res.getHeader("Content-Disposition"));
        byte[] bytes = res.getContentAsByteArray();
        assertTrue(bytes.length > 0);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("Report");
            assertNotNull(sheet);
            Row header = sheet.getRow(0);
            assertEquals("Akun", header.getCell(0).getStringCellValue());
            assertEquals("Debit", header.getCell(1).getStringCellValue());
            assertEquals("Aktif", header.getCell(2).getStringCellValue());
            assertTrue(wb.getFontAt(header.getCell(0).getCellStyle().getFontIndex()).getBold());
            assertEquals(100.5, sheet.getRow(1).getCell(1).getNumericCellValue(), 0.0001);
            assertTrue(sheet.getRow(1).getCell(2).getBooleanCellValue());
            assertEquals("4.1", sheet.getRow(2).getCell(0).getStringCellValue());
            assertNull(sheet.getRow(2).getCell(2), "kolom di luar header baris pertama diabaikan");
        }

        Result bad = call(post("/api/erp/reports/export", Map.of("data", "x")));
        assertEquals(400, bad.status());
        assertEquals("Data tidak valid untuk ekspor.", bad.error());
    }

    @Test
    void validation_errors_match_source() throws Exception {
        asAdmin();
        Result r = call(get("/api/erp/reports/trial-balance?startDate=bukan-tanggal"));
        assertEquals(400, r.status());
        assertEquals("Parameter startDate tidak valid.", r.error());
        r = call(get("/api/erp/reports/profit-loss?endDate=2041-13-01"));
        assertEquals(400, r.status());
        assertEquals("Parameter endDate tidak valid.", r.error());
        r = call(get("/api/erp/reports/balance-sheet?date=xx"));
        assertEquals("Parameter date tidak valid.", r.error());
        r = call(get("/api/erp/reports/ledger-detail"));
        assertEquals(400, r.status());
        assertEquals("Parameter accountId wajib diisi.", r.error());
        r = call(get("/api/erp/reports/ledger-detail?accountId=tidak-ada"));
        assertEquals(404, r.status());
        assertEquals("Akun tidak ditemukan.", r.error());

        asUser("tanpa.izin.laporan@test.local", customRole(Map.of("inventory", "view")));
        assertEquals(403, call(get("/api/erp/reports/trial-balance")).status());
        String viewer = customRole(Map.of("reports", "view"));
        asUser("viewer.laporan@test.local", viewer);
        assertEquals(200, call(get("/api/erp/reports/trial-balance")).status());
        assertEquals(403, call(post("/api/erp/reports/export", Map.of("data", List.of()))).status(),
                "ekspor (POST) butuh reports edit");
    }
}
