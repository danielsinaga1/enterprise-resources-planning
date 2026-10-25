package id.neravoxa.erp.cashbook;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Impor CSV/Excel (COA, mitra, jurnal, persediaan + stok awal) dan unduh template. */
class ImportFlowTest extends CashBookTestSupport {

    private static final String BASE = "/api/erp/import";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Workbook satu sheet; sel Number → numerik, LocalDate → sel tanggal, null → kosong, selain itu teks. */
    private static byte[] xlsx(List<String> headers, List<List<Object>> rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            CreationHelper helper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));
            Row h = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                h.createCell(c).setCellValue(headers.get(c));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<Object> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    Object v = values.get(c);
                    if (v == null) {
                        continue;
                    }
                    Cell cell = row.createCell(c);
                    if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else if (v instanceof LocalDate d) {
                        cell.setCellValue(d);
                        cell.setCellStyle(dateStyle);
                    } else {
                        cell.setCellValue(v.toString());
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static List<Object> row(Object... values) {
        List<Object> l = new ArrayList<>();
        for (Object v : values) {
            l.add(v);
        }
        return l;
    }

    private MockHttpServletRequestBuilder upload(String path, String filename, byte[] content, boolean preview) {
        var b = MockMvcRequestBuilders.multipart(BASE + path).file(new MockMultipartFile("file", filename, XLSX, content));
        if (preview) {
            b.param("preview", "true");
        }
        return b;
    }

    private static List<String> messages(JsonNode errors) {
        List<String> out = new ArrayList<>();
        errors.forEach(e -> out.add(e.get("row").asInt() + ":" + e.get("message").asText()));
        return out;
    }

    @Test
    void coa_preview_reports_row_errors_and_commit_posts_opening_balances() throws Exception {
        asAdmin();
        BigDecimal equity0 = balance("3.1");
        byte[] file = xlsx(List.of("accountNumber", "name", "rootType", "accountType", "parentNumber", "isGroup", "openingBalance"),
                List.of(
                        row("9.91.1", "Kas Uji Impor", "asset", null, null, null, 500000),
                        row("9.91", "Aset Uji Impor", "ASSET", null, null, "false", null),
                        row("9.91.2", "Hutang Uji Impor", "LIABILITY", null, "9.91", null, "200000"),
                        row(null, "Tanpa nomor", "ASSET"),
                        row("9.91.3", "Tipe salah", "FOO"),
                        row("9.91.4", "Saldo salah", "ASSET", null, null, null, "abc"),
                        row("9.91.5", "=SUM(A1)", "EXPENSE")));

        JsonNode preview = ok(upload("/coa", "coa.xlsx", file, true));
        assertEquals(7, preview.get("total").asInt());
        assertEquals(List.of("5:Kolom \"accountNumber\" wajib diisi.", "6:rootType \"FOO\" tidak valid.",
                "7:openingBalance \"abc\" bukan angka valid."), messages(preview.get("errors")));
        JsonNode data = preview.get("data");
        assertEquals(4, data.size());
        assertEquals("9.91", data.get(0).get("accountNumber").asText());
        assertTrue(data.get(0).get("isGroup").asBoolean(), "induk terdeteksi otomatis sebagai grup");
        assertEquals("9.91.1", data.get(1).get("accountNumber").asText());
        assertEquals("ASSET", data.get(1).get("rootType").asText());
        assertEquals("ASSET", data.get(1).get("accountType").asText());
        assertEquals(500000, data.get(1).get("openingBalance").asLong());
        assertEquals("'=SUM(A1)", data.get(3).get("name").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE account_number LIKE '9.91%'", Integer.class));

        JsonNode res = ok(upload("/coa", "coa.xlsx", file, false));
        assertEquals(4, res.get("success").asInt());
        assertEquals(3, res.get("failed").asInt());
        assertEquals(3, res.get("errors").size());
        Map<String, Object> child = jdbc.queryForMap("SELECT a.is_group, a.balance, p.account_number AS parent FROM accounts a "
                + "LEFT JOIN accounts p ON p.id = a.parent_id WHERE a.account_number = '9.91.1'");
        assertEquals("9.91", child.get("parent"));
        assertEquals(false, child.get("is_group"));
        assertMoney("500000", (BigDecimal) child.get("balance"));
        assertMoney("200000", balance("9.91.2"));
        assertEquals(true, jdbc.queryForObject("SELECT is_group FROM accounts WHERE account_number = '9.91'", Boolean.class));
        String j1 = jdbc.queryForObject("SELECT id FROM journal_entries WHERE narration = 'Saldo awal akun: 9.91.1 - Kas Uji Impor'",
                String.class);
        assertJournal(j1, "9.91.1", "3.1", "500000");
        String j2 = jdbc.queryForObject("SELECT id FROM journal_entries WHERE narration = 'Saldo awal akun: 9.91.2 - Hutang Uji Impor'",
                String.class);
        assertJournal(j2, "3.1", "9.91.2", "200000");
        // Ekuitas: kredit 500.000 (aset) dan debit 200.000 (kewajiban) → +300.000.
        assertMoney("300000", balance("3.1").subtract(equity0));

        // Impor ulang tidak menggandakan jurnal saldo awal maupun saldo akun.
        ok(upload("/coa", "coa.xlsx", file, false));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM journal_entries WHERE narration LIKE 'Saldo awal akun: 9.91.1 %'",
                Integer.class));
        assertMoney("500000", balance("9.91.1"));
        assertMoney("300000", balance("3.1").subtract(equity0));
    }

    @Test
    void parties_csv_preview_and_commit_create_opening_journals_and_deposits() throws Exception {
        asAdmin();
        String u = word();
        String csv = "﻿name,partyType,phone,email,address,taxId,openingBalance,depositBalance,customerDepositBalance\n"
                + "PT Pelanggan " + u + ",Customer,0812, ,\"Jl. A, No. 1\",,1500000,,300000\n"
                + "CV Pemasok " + u + ",Supplier,,,,01.234,750000,250000,\n"
                + "Salah " + u + ",Vendor,,,,,,,\n"
                + ",Customer,,,,,,,\n"
                + "Angka " + u + ",Both,,,,,x12,,\n";
        byte[] file = csv.getBytes(StandardCharsets.UTF_8);
        var previewReq = MockMvcRequestBuilders.multipart(BASE + "/parties")
                .file(new MockMultipartFile("file", "parties.csv", "text/csv", file)).param("preview", "true");
        JsonNode preview = ok(previewReq);
        assertEquals(5, preview.get("total").asInt());
        assertEquals(List.of("4:partyType \"Vendor\" tidak valid. Gunakan: Customer, Supplier, atau Both.",
                "5:Kolom \"name\" wajib diisi.", "6:openingBalance \"x12\" bukan angka valid."), messages(preview.get("errors")));
        assertEquals(2, preview.get("data").size());
        JsonNode p0 = preview.get("data").get(0);
        assertEquals("Jl. A, No. 1", p0.get("address").asText());
        assertFalse(p0.has("email"), "kolom kosong tidak dikirim (undefined)");
        assertFalse(p0.has("taxId"));
        assertEquals(1500000, p0.get("openingBalance").asLong());
        assertEquals("01.234", preview.get("data").get(1).get("taxId").asText());

        BigDecimal ar0 = balance("1.2.1");
        BigDecimal ap0 = balance("2.1.1");
        var commitReq = MockMvcRequestBuilders.multipart(BASE + "/parties")
                .file(new MockMultipartFile("file", "parties.csv", "text/csv", file));
        JsonNode res = ok(commitReq);
        assertEquals(2, res.get("success").asInt());
        assertEquals(3, res.get("failed").asInt());

        Map<String, Object> cust = jdbc.queryForMap("SELECT id, outstanding_amount, deposit_balance, customer_deposit_balance, phone "
                + "FROM parties WHERE name = ?", "PT Pelanggan " + u);
        assertMoney("1500000", (BigDecimal) cust.get("outstanding_amount"));
        assertMoney("300000", (BigDecimal) cust.get("customer_deposit_balance"));
        assertMoney("0", (BigDecimal) cust.get("deposit_balance"));
        assertEquals("0812", cust.get("phone"));
        Map<String, Object> supp = jdbc.queryForMap("SELECT id, outstanding_amount, deposit_balance FROM parties WHERE name = ?",
                "CV Pemasok " + u);
        assertMoney("750000", (BigDecimal) supp.get("outstanding_amount"));
        assertMoney("250000", (BigDecimal) supp.get("deposit_balance"));

        String jAr = jdbc.queryForObject("SELECT id FROM journal_entries WHERE narration = ?", String.class,
                "Saldo awal piutang: PT Pelanggan " + u);
        assertJournal(jAr, "1.2.1", "3.1", "1500000");
        String partyOnLedger = jdbc.queryForObject("SELECT party_id FROM accounting_ledger_entries WHERE reference_id = ? "
                + "AND debit > 0", String.class, jAr);
        assertEquals(cust.get("id"), partyOnLedger);
        String jAp = jdbc.queryForObject("SELECT id FROM journal_entries WHERE narration = ?", String.class,
                "Saldo awal hutang: CV Pemasok " + u);
        assertJournal(jAp, "3.1", "2.1.1", "750000");
        String jVd = jdbc.queryForObject("SELECT id FROM journal_entries WHERE narration = ?", String.class,
                "Saldo awal uang muka vendor: CV Pemasok " + u);
        assertJournal(jVd, "1.3", "3.1", "250000");
        String jCd = jdbc.queryForObject("SELECT id FROM journal_entries WHERE narration = ?", String.class,
                "Saldo awal uang muka pelanggan: PT Pelanggan " + u);
        assertJournal(jCd, "3.1", "2.1.2", "300000");
        Map<String, Object> pay = jdbc.queryForMap("SELECT payment_type::text AS t, amount, status::text AS s, journal_entry_id "
                + "FROM payments WHERE party_id = ?", supp.get("id"));
        assertEquals("VendorDeposit", pay.get("t"));
        assertEquals("Submitted", pay.get("s"));
        assertMoney("250000", (BigDecimal) pay.get("amount"));
        assertEquals(jVd, pay.get("journal_entry_id"));
        assertMoney("1500000", balance("1.2.1").subtract(ar0));
        assertMoney("750000", balance("2.1.1").subtract(ap0));

        // Impor ulang: jurnal & payment saldo awal lama diganti, tidak menggandakan.
        ok(MockMvcRequestBuilders.multipart(BASE + "/parties").file(new MockMultipartFile("file", "parties.csv", "text/csv", file)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM journal_entries WHERE narration = ?", Integer.class,
                "Saldo awal piutang: PT Pelanggan " + u));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE party_id = ?", Integer.class, supp.get("id")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM parties WHERE name = ?", Integer.class, "PT Pelanggan " + u));
        assertMoney("1500000", balance("1.2.1").subtract(ar0));
    }

    @Test
    void journals_group_by_date_and_narration_and_report_unbalanced_groups() throws Exception {
        asAdmin();
        String u = word();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        byte[] file = xlsx(List.of("date", "narration", "accountNumber", "debit", "credit", "description"), List.of(
                row(today, "Jurnal impor " + u, "6.1", 100000, 0, "Beban internet"),
                row(today, "Jurnal impor " + u, "1.1.1", null, 100000, null),
                row("2026-10-01", "Tidak seimbang " + u, "6.1", 5000, 0),
                row("2026-10-01", "Tidak seimbang " + u, "1.1.1", 0, 4000),
                row(null, "Tanpa tanggal " + u, "6.1", 1, 0),
                row(today.toString(), "Akun hilang " + u, "9.99.99", 10, 0),
                row(today.toString(), "Akun hilang " + u, "1.1.1", 0, 10)));

        JsonNode preview = ok(upload("/journals", "jurnal.xlsx", file, true));
        assertEquals(7, preview.get("total").asInt());
        assertEquals(2, preview.get("journalCount").asInt());
        assertEquals(List.of("6:Kolom \"date\" wajib diisi.",
                "4:Jurnal \"Tidak seimbang " + u + "\" tidak seimbang: Debit=5000, Credit=4000",
                "5:Jurnal \"Tidak seimbang " + u + "\" tidak seimbang: Debit=5000, Credit=4000"), messages(preview.get("errors")));
        JsonNode g0 = preview.get("data").get(0);
        assertEquals(today.toString(), g0.get("date").asText());
        assertEquals(2, g0.get("items").size());
        assertEquals(100000, g0.get("items").get(0).get("debit").asLong());
        assertEquals("", g0.get("items").get(1).get("description").asText());

        JsonNode res = ok(upload("/journals", "jurnal.xlsx", file, false));
        assertEquals(1, res.get("success").asInt());
        assertEquals(1, res.get("failed").asInt());
        assertEquals(2, res.get("totalJournals").asInt());
        List<String> errs = messages(res.get("errors"));
        assertTrue(errs.contains("0:Gagal import jurnal \"Akun hilang " + u + "\": Akun \"9.99.99\" tidak ditemukan."), errs.toString());

        Map<String, Object> je = jdbc.queryForMap("SELECT id, date, status::text AS s FROM journal_entries WHERE narration = ?",
                "Jurnal impor " + u);
        assertJournal((String) je.get("id"), "6.1", "1.1.1", "100000");
        assertEquals(today.toString(), je.get("date").toString().substring(0, 10));
        String fallback = jdbc.queryForObject("SELECT description FROM journal_entry_accounts WHERE journal_entry_id = ? AND credit > 0",
                String.class, je.get("id"));
        assertEquals("Jurnal impor " + u, fallback);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM journal_entries WHERE narration = ?", Integer.class,
                "Akun hilang " + u));
    }

    @Test
    void inventory_import_posts_opening_stock_with_weighted_average() throws Exception {
        asAdmin();
        String u = word().toUpperCase();
        String codeA = "IMP-" + u + "-A";
        String codeB = "IMP-" + u + "-B";
        String codeC = "IMP-" + u + "-C";
        List<String> headers = List.of("code", "name", "unit", "category", "description", "minimumStock", "openingQty",
                "openingPrice", "warehouseCode");
        byte[] file = xlsx(headers, List.of(
                row(codeA, "Barang A " + u, "Kg", "Bahan Baku", null, 10, 100, 2500, null),
                row(codeB, "Barang B " + u, "Pcs", null, null, null, 5, "1000", "GDG-UTAMA"),
                row(codeC, "Barang C " + u, "Pcs", null, null, null, 5, 1000, "TIDAK-ADA"),
                row(null, "Tanpa kode", "Kg"),
                row("IMP-" + u + "-D", "Barang D", "Kg", null, null, "x1")));
        BigDecimal inv0 = balance("1.4.0");

        JsonNode preview = ok(upload("/inventory", "barang.xlsx", file, true));
        assertEquals(5, preview.get("total").asInt());
        assertEquals(List.of("5:Kolom \"code\" wajib diisi.", "6:minimumStock \"x1\" bukan angka valid."),
                messages(preview.get("errors")));
        assertEquals(3, preview.get("data").size());
        assertEquals(10, preview.get("data").get(0).get("minimumStock").asInt());
        assertFalse(preview.get("data").get(1).has("minimumStock"));
        assertEquals("GDG-UTAMA", preview.get("data").get(1).get("warehouseCode").asText());

        JsonNode res = ok(upload("/inventory", "barang.xlsx", file, false));
        assertEquals(2, res.get("success").asInt());
        assertEquals(3, res.get("failed").asInt());
        assertTrue(messages(res.get("errors")).contains(
                "0:Gagal insert \"" + codeC + "\": Gudang dengan kode \"TIDAK-ADA\" tidak ditemukan."));

        Map<String, Object> a = jdbc.queryForMap("SELECT id, current_stock, average_cost, minimum_stock, category FROM inventory_items "
                + "WHERE code = ?", codeA);
        assertMoney("100", (BigDecimal) a.get("current_stock"));
        assertMoney("2500", (BigDecimal) a.get("average_cost"));
        assertMoney("10", (BigDecimal) a.get("minimum_stock"));
        assertEquals("Bahan Baku", a.get("category"));
        BigDecimal whStock = jdbc.queryForObject("SELECT s.current_stock FROM inventory_stocks s JOIN warehouses w ON w.id = s.warehouse_id "
                + "WHERE s.item_id = ? AND w.code = 'GDG-UTAMA'", BigDecimal.class, a.get("id"));
        assertMoney("100", whStock);
        Map<String, Object> mv = jdbc.queryForMap("SELECT movement_type::text AS t, quantity, unit_cost, total_value, reference_type, "
                + "journal_entry_id, offset_account_id FROM stock_movements WHERE item_id = ?", a.get("id"));
        assertEquals("In", mv.get("t"));
        assertEquals("OpeningBalance", mv.get("reference_type"));
        assertMoney("250000", (BigDecimal) mv.get("total_value"));
        assertEquals(accountId("3.1"), mv.get("offset_account_id"));
        assertJournal((String) mv.get("journal_entry_id"), "1.4.0", "3.1", "250000");
        // Barang C tetap tersimpan (upsert terpisah dari posting stok awal), tanpa stok.
        assertMoney("0", jdbc.queryForObject("SELECT current_stock FROM inventory_items WHERE code = ?", BigDecimal.class, codeC));
        assertMoney("255000", balance("1.4.0").subtract(inv0));

        // Stok awal kedua memadukan rata-rata: (100×2.500 + 100×3.500) / 200 = 3.000.
        byte[] second = xlsx(headers, List.of(row(codeA, "Barang A " + u, "Kg", null, null, null, 100, 3500, null)));
        JsonNode res2 = ok(upload("/inventory", "barang2.xlsx", second, false));
        assertEquals(1, res2.get("success").asInt());
        Map<String, Object> a2 = jdbc.queryForMap("SELECT current_stock, average_cost, category, minimum_stock FROM inventory_items "
                + "WHERE code = ?", codeA);
        assertMoney("200", (BigDecimal) a2.get("current_stock"));
        assertMoney("3000", (BigDecimal) a2.get("average_cost"));
        assertEquals("Bahan Baku", a2.get("category"), "kolom kosong tidak menimpa data lama");
        assertMoney("10", (BigDecimal) a2.get("minimum_stock"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE item_id = ?", Integer.class, a.get("id")));
    }

    @Test
    void upload_guards_and_template_download() throws Exception {
        asAdmin();
        assertEquals("File wajib diunggah.", call(MockMvcRequestBuilders.multipart(BASE + "/coa")).error());
        assertEquals("Format file tidak didukung. Gunakan CSV atau Excel (.xlsx/.xls).",
                call(MockMvcRequestBuilders.multipart(BASE + "/coa")
                        .file(new MockMultipartFile("file", "data.txt", "text/plain", "a,b".getBytes()))).error());
        assertEquals("Ukuran file maksimal 5 MB.",
                call(MockMvcRequestBuilders.multipart(BASE + "/coa")
                        .file(new MockMultipartFile("file", "big.csv", "text/csv", new byte[5 * 1024 * 1024 + 1]))).error());
        assertEquals("Gagal memproses file.",
                call(MockMvcRequestBuilders.multipart(BASE + "/coa")
                        .file(new MockMultipartFile("file", "rusak.xlsx", XLSX, "bukan excel".getBytes()))).error());
        assertEquals("Invalid Record Length: columns length is 3, got 2 on line 3",
                call(MockMvcRequestBuilders.multipart(BASE + "/coa")
                        .file(new MockMultipartFile("file", "x.csv", "text/csv", "a,b,c\n1,2,3\n4,5\n".getBytes()))).error());
        Result unknown = call(get(BASE + "/template/journals"));
        assertEquals(400, unknown.status());
        assertEquals("Tipe \"journals\" tidak didukung. Gunakan: coa, parties, inventory.", unknown.error());

        for (String type : List.of("coa", "parties", "inventory")) {
            MvcResult r = mvc.perform(get(BASE + "/template/" + type).with(req -> {
                req.setUserPrincipal(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
                return req;
            })).andReturn();
            assertEquals(200, r.getResponse().getStatus());
            assertTrue(r.getResponse().getContentType().startsWith(XLSX));
            assertEquals("attachment; filename=\"template_" + type + ".xlsx\"", r.getResponse().getHeader("Content-Disposition"));
            try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(r.getResponse().getContentAsByteArray()))) {
                Sheet sheet = wb.getSheet("Data");
                Row header = sheet.getRow(0);
                assertTrue(wb.getFontAt(header.getCell(0).getCellStyle().getFontIndex()).getBold());
                List<String> heads = new ArrayList<>();
                header.forEach(c -> heads.add(c.getStringCellValue()));
                switch (type) {
                    case "coa" -> {
                        assertEquals(List.of("accountNumber", "name", "rootType", "accountType", "parentNumber", "isGroup",
                                "openingBalance"), heads);
                        boolean found = false;
                        for (Row row : sheet) {
                            if (row.getRowNum() > 0 && "1.1.1".equals(row.getCell(0).getStringCellValue())) {
                                found = true;
                                assertEquals("1.1", row.getCell(4).getStringCellValue());
                                assertEquals("false", row.getCell(5).getStringCellValue());
                            }
                        }
                        assertTrue(found);
                    }
                    case "parties" -> assertEquals(List.of("name", "partyType", "phone", "email", "address", "taxId",
                            "openingBalance", "depositBalance", "customerDepositBalance"), heads);
                    default -> assertEquals(List.of("code", "name", "unit", "category", "description", "minimumStock",
                            "openingQty", "openingPrice", "warehouseCode"), heads);
                }
            }
        }

        // Unduh template cukup login (requireWrite: GET terbuka); unggah butuh edit pengaturan.
        asUser("tanpa.izin.impor@test.local", "SOME_OTHER_ROLE");
        assertEquals(403, call(MockMvcRequestBuilders.multipart(BASE + "/coa")
                .file(new MockMultipartFile("file", "x.csv", "text/csv", "a\n1\n".getBytes()))).status());
    }
}
