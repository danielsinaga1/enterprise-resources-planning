package id.enterpriseapps.erp.adminsetup;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** /api/erp/parties: CRUD + validasi, mode daftar & lama, ringkasan, mitra serupa, guard hapus, dashboard. */
class PartyFlowTest extends AdminSetupTestSupport {

    private static final String DENIED = "Akses ditolak: Tidak memiliki izin.";
    private static final String TYPE_MSG = "Invalid option: expected one of \"Customer\"|\"Supplier\"|\"Both\"";

    private String createParty(String name, String type) throws Exception {
        return ok(post("/api/erp/parties", Map.of("name", name, "partyType", type))).get("id").asText();
    }

    private String fiscalYearId() {
        return jdbc.queryForObject("SELECT id FROM fiscal_years WHERE name = EXTRACT(YEAR FROM now())::int::text", String.class);
    }

    private static LocalDateTime utc(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private String invoice(String table, String partyId, String amount, int daysAgo, String userId) {
        String id = UUID.randomUUID().toString();
        BigDecimal a = new BigDecimal(amount);
        jdbc.update("INSERT INTO " + table + " (id, invoice_number, date, party_id, status, grand_total, outstanding, "
                        + "fiscal_year_id, created_by, created_at, updated_at) VALUES (?, ?, ?, ?, 'Submitted', ?, ?, ?, ?, now(), now())",
                id, uniq("INV"), utc(Instant.now().minus(daysAgo, ChronoUnit.DAYS)), partyId, a, a, fiscalYearId(), userId);
        return id;
    }

    private String payment(String partyId, String amount, Instant date, String userId) {
        String id = UUID.randomUUID().toString();
        String account = jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = '1.1.1'", String.class);
        jdbc.update("INSERT INTO payments (id, payment_number, date, payment_type, party_id, account_id, amount, status, "
                        + "created_by, fiscal_year_id, created_at, updated_at) VALUES (?, ?, ?, 'Receive', ?, ?, ?, 'Submitted', ?, ?, now(), now())",
                id, uniq("PAY"), utc(date), partyId, account, new BigDecimal(amount), userId, fiscalYearId());
        return id;
    }

    @Test
    void create_get_update_with_source_validation() throws Exception {
        adminId();
        assertError(call(post("/api/erp/parties", Map.of())), 400, "Invalid input: expected string, received undefined");
        assertError(call(post("/api/erp/parties", Map.of("name", ""))), 400, "Nama wajib diisi.");
        assertError(call(post("/api/erp/parties", Map.of("name", "X"))), 400, TYPE_MSG);
        assertError(call(post("/api/erp/parties", Map.of("name", "X", "partyType", "Vendor"))), 400, TYPE_MSG);
        assertError(call(post("/api/erp/parties", Map.of("name", "X", "partyType", "Customer", "email", "salah"))), 400,
                "Format email tidak valid.");
        assertError(call(post("/api/erp/parties", Map.of("name", "X", "partyType", "Customer", "isWapu", "ya"))), 400,
                "Invalid input: expected boolean, received string");

        String name = uniq("PT Mitra");
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("partyType", "Customer");
        body.put("phone", "");
        body.put("email", "");
        body.put("address", "Jl. Mitra");
        body.put("taxId", "01.234");
        body.put("isWapu", true);
        Result created = call(post("/api/erp/parties", body));
        assertEquals(201, created.status(), created.body().toString());
        JsonNode p = created.data();
        String id = p.get("id").asText();
        assertEquals(name, p.get("name").asText());
        assertEquals("Customer", p.get("partyType").asText());
        assertTrue(p.get("phone").isNull());
        assertTrue(p.get("email").isNull());
        assertEquals("Jl. Mitra", p.get("address").asText());
        assertTrue(p.get("isWapu").asBoolean());
        assertTrue(p.get("isActive").asBoolean());
        assertEquals(0, p.get("outstandingAmount").decimalValue().signum());
        assertTrue(p.has("createdAt") && p.has("updatedAt") && p.has("customerDepositBalance"));
        assertFalse(p.has("salesInvoices"), "relasi yang tidak di-include tidak boleh muncul");

        assertEquals(name, ok(get("/api/erp/parties/" + id)).get("name").asText());
        assertError(call(get("/api/erp/parties/" + UUID.randomUUID())), 404, "Pihak tidak ditemukan.");

        Map<String, Object> upd = new HashMap<>();
        upd.put("phone", "  0812  ");
        upd.put("address", null);
        upd.put("taxId", "   ");
        upd.put("isActive", false);
        JsonNode u = ok(put("/api/erp/parties/" + id, upd));
        assertEquals("0812", u.get("phone").asText());
        assertTrue(u.get("address").isNull());
        assertTrue(u.get("taxId").isNull(), "spasi saja dikosongkan (clearable)");
        assertFalse(u.get("isActive").asBoolean());
        assertEquals(name, u.get("name").asText());
        // Zod 4: default(false) isWapu tetap berlaku di skema partial → tidak dikirim = false.
        assertFalse(u.get("isWapu").asBoolean());
        JsonNode wapu = ok(put("/api/erp/parties/" + id, Map.of("isWapu", true, "partyType", "Both")));
        assertTrue(wapu.get("isWapu").asBoolean());
        assertEquals("Both", wapu.get("partyType").asText());

        assertError(call(put("/api/erp/parties/" + id, Map.of("name", ""))), 400, "Nama wajib diisi.");
        assertError(call(put("/api/erp/parties/" + id, Map.of("email", "  "))), 400, "Format email tidak valid.");
        assertError(call(put("/api/erp/parties/" + id, Map.of("partyType", "Vendor"))), 400, TYPE_MSG);
        assertError(call(put("/api/erp/parties/" + UUID.randomUUID(), Map.of("name", "Y"))), 404, "Data pihak tidak ditemukan.");
    }

    @Test
    void list_modes_summary_and_similar() throws Exception {
        adminId();
        String tag = "zq" + UUID.randomUUID().toString().substring(0, 8);
        String a = createParty(tag + " Alfa Pelanggan", "Customer");
        String b = createParty(tag + " Bravo Vendor", "Supplier");
        String c = createParty(tag + " Charlie Dua Sisi", "Both");
        String d = createParty(tag + " Delta Pelanggan", "Customer");
        ok(put("/api/erp/parties/" + c, Map.of("isActive", false)));
        jdbc.update("UPDATE parties SET outstanding_amount = 150000.50 WHERE id = ?", a);
        jdbc.update("UPDATE parties SET outstanding_amount = -2500 WHERE id = ?", b);
        jdbc.update("UPDATE parties SET customer_deposit_balance = 7000, deposit_balance = 300 WHERE id = ?", d);
        jdbc.update("UPDATE parties SET deposit_balance = 1200 WHERE id = ?", c);

        JsonNode list = ok(get("/api/erp/parties?search=" + tag + "&sort=name"));
        assertEquals(4, list.get("total").asInt());
        assertEquals(25, list.get("limit").asInt());
        assertEquals(List.of(a, b, c, d), texts(list.get("data"), "id"));
        assertEquals(500, ok(get("/api/erp/parties?search=" + tag + "&limit=900")).get("limit").asInt());
        assertEquals(List.of(b, c), texts(ok(get("/api/erp/parties?search=" + tag + "&type=Supplier&sort=name")).get("data"), "id"));
        assertEquals(List.of(c), texts(ok(get("/api/erp/parties?search=" + tag + "&type=Both")).get("data"), "id"));
        assertEquals(List.of(a, c, d), texts(ok(get("/api/erp/parties?search=" + tag + "&type=Customer&sort=name")).get("data"), "id"));
        assertEquals(List.of(c), texts(ok(get("/api/erp/parties?search=" + tag + "&isActive=false")).get("data"), "id"));
        assertEquals(List.of(a), texts(ok(get("/api/erp/parties?search=" + tag + "&hasOutstanding=true")).get("data"), "id"));
        // Seri outstanding jatuh ke nama asc, lalu id.
        assertEquals(List.of(a, c, d, b), texts(ok(get("/api/erp/parties?search=" + tag + "&sort=-outstanding")).get("data"), "id"));
        // deposit: tab Vendor memakai uang muka vendor dulu, selainnya uang muka pelanggan dulu.
        assertEquals(List.of(c, b), texts(ok(get("/api/erp/parties?search=" + tag + "&type=Supplier&sort=-deposit")).get("data"), "id"));
        assertEquals(List.of(d, c, a, b), texts(ok(get("/api/erp/parties?search=" + tag + "&sort=-deposit")).get("data"), "id"));
        assertError(call(get("/api/erp/parties?search=" + tag + "&type=Foo")), 400, "Jenis mitra tidak dikenal.");
        assertError(call(get("/api/erp/parties?isActive=maybe")), 400, "Filter status mitra tidak valid.");
        assertError(call(get("/api/erp/parties?hasOutstanding=x")), 400, "Filter saldo terutang tidak valid.");

        // Mode lama (tanpa parameter mode daftar): limit maks 200, Customer menyertakan Both.
        JsonNode legacy = ok(get("/api/erp/parties?type=Customer&limit=1000"));
        assertEquals(200, legacy.get("limit").asInt());
        assertEquals(1, legacy.get("page").asInt());
        legacy.get("data").forEach(n -> assertTrue(List.of("Customer", "Both").contains(n.get("partyType").asText())));
        assertError(call(get("/api/erp/parties?page=0")), 500, "Gagal mengambil data pihak.");
        assertError(call(get("/api/erp/parties?type=Foo")), 500, "Gagal mengambil data pihak.");

        JsonNode s = ok(get("/api/erp/parties/summary?search=" + tag));
        assertEquals(4, s.get("total").asInt());
        assertEquals(3, s.get("activeCount").asInt());
        assertEquals(1, s.get("inactiveCount").asInt());
        assertEquals(2, s.get("byType").get("Customer").asInt());
        assertEquals(1, s.get("byType").get("Supplier").asInt());
        assertEquals(1, s.get("byType").get("Both").asInt());
        assertEquals(0, new BigDecimal("150000.50").compareTo(s.get("totalReceivable").decimalValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(s.get("totalPayable").decimalValue()));
        assertEquals(0, new BigDecimal("7000").compareTo(s.get("totalCustomerDeposit").decimalValue()));
        assertEquals(0, new BigDecimal("1200").compareTo(s.get("totalVendorDeposit").decimalValue()));
        assertEquals(1, s.get("withOutstandingCount").asInt());
        assertEquals(1, ok(get("/api/erp/parties/summary?search=" + tag + "&type=Supplier&isActive=true")).get("total").asInt());
        assertError(call(get("/api/erp/parties/summary?type=Foo")), 400, "Jenis mitra tidak dikenal.");

        JsonNode similar = ok(get("/api/erp/parties/similar?name=" + tag + " Alfa Pelangan&type=Customer")).get("data");
        assertEquals(a, similar.get(0).get("id").asText());
        assertTrue(similar.get(0).get("similarity").asDouble() >= 0.6);
        assertEquals(List.of("id", "name", "partyType", "phone", "isActive", "similarity"),
                List.copyOf(iterable(similar.get(0).fieldNames())));
        JsonNode supplierOnly = ok(get("/api/erp/parties/similar?name=" + tag + " Alfa Pelangan&type=Supplier")).get("data");
        assertFalse(texts(supplierOnly, "id").contains(a));
        assertEquals(0, ok(get("/api/erp/parties/similar?name=a")).get("data").size());
    }

    @Test
    void search_treats_like_wildcards_literally() throws Exception {
        adminId();
        String tag = "zw" + UUID.randomUUID().toString().substring(0, 8);
        String literal = createParty(tag + " Kadar 100%_Murni", "Customer");
        createParty(tag + " Kadar 1000 Murni", "Customer");
        assertEquals(List.of(literal),
                texts(ok(get("/api/erp/parties?search=" + tag + " kadar 100%_m")).get("data"), "id"));
        assertEquals(1, ok(get("/api/erp/parties/summary?search=" + tag + " kadar 100%_")).get("total").asInt());
        assertEquals(2, ok(get("/api/erp/parties?search=" + tag + " kadar")).get("total").asInt());
    }

    @Test
    void delete_deactivates_linked_parties_and_needs_doc_cancel() throws Exception {
        String adminId = adminId();
        String linked = createParty(uniq("PT Bertransaksi"), "Customer");
        String free = createParty(uniq("PT Bebas"), "Supplier");
        invoice("sales_invoices", linked, "1000", 1, adminId);
        payment(linked, "500", Instant.now(), adminId);

        JsonNode deact = ok(delete("/api/erp/parties/" + linked));
        assertEquals("Mitra dinonaktifkan karena memiliki 2 transaksi terkait.", deact.get("message").asText());
        assertTrue(deact.get("deactivated").asBoolean());
        assertEquals(false, jdbc.queryForObject("SELECT is_active FROM parties WHERE id = ?", Boolean.class, linked));

        JsonNode deleted = ok(delete("/api/erp/parties/" + free));
        assertEquals("Mitra dihapus.", deleted.get("message").asText());
        assertTrue(deleted.get("deleted").asBoolean());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM parties WHERE id = ?", Integer.class, free));
        assertError(call(delete("/api/erp/parties/" + UUID.randomUUID())), 404, "Data mitra tidak ditemukan.");

        // ERP_PRODUCTION boleh edit mitra tetapi tanpa doc.cancel; ERP_VIEWER hanya boleh membaca.
        String prod = uniq("prod").toLowerCase() + "@corp.test";
        platformUser("Produksi Uji", null, prod, null, true, "ERP_PRODUCTION");
        login(prod, "ERP_PRODUCTION");
        String other = createParty(uniq("PT Produksi"), "Supplier");
        assertError(call(delete("/api/erp/parties/" + other)), 403, DENIED);
        String viewer = uniq("pview").toLowerCase() + "@corp.test";
        platformUser("Viewer Mitra", null, viewer, null, true, "ERP_VIEWER");
        login(viewer, "ERP_VIEWER");
        assertEquals(other, ok(get("/api/erp/parties/" + other)).get("id").asText());
        assertError(call(post("/api/erp/parties", Map.of("name", "X", "partyType", "Customer"))), 403, DENIED);
        assertError(call(put("/api/erp/parties/" + other, Map.of("name", "Y"))), 403, DENIED);
    }

    @Test
    void dashboard_aggregates_aging_and_recent_payments() throws Exception {
        String adminId = adminId();
        JsonNode before = ok(get("/api/erp/parties/dashboard"));
        String cust = createParty(uniq("PT Dashboard Pelanggan"), "Customer");
        String supp = createParty(uniq("PT Dashboard Pemasok"), "Supplier");
        jdbc.update("UPDATE parties SET outstanding_amount = 987654321.00, customer_deposit_balance = 250 WHERE id = ?", cust);
        jdbc.update("UPDATE parties SET outstanding_amount = 400, deposit_balance = 75 WHERE id = ?", supp);
        String si = invoice("sales_invoices", cust, "1234.50", 45, adminId);
        invoice("purchase_invoices", supp, "800", 100, adminId);
        String pay = payment(cust, "321", Instant.now().plus(2, ChronoUnit.DAYS), adminId);

        JsonNode after = ok(get("/api/erp/parties/dashboard"));
        JsonNode sb = before.get("summary");
        JsonNode sa = after.get("summary");
        assertEquals(sb.get("totalCustomers").asInt() + 1, sa.get("totalCustomers").asInt());
        assertEquals(sb.get("totalSuppliers").asInt() + 1, sa.get("totalSuppliers").asInt());
        assertEquals(0, sb.get("totalPiutang").decimalValue().add(new BigDecimal("987654321")).compareTo(sa.get("totalPiutang").decimalValue()));
        assertEquals(0, sb.get("totalHutang").decimalValue().add(new BigDecimal("400")).compareTo(sa.get("totalHutang").decimalValue()));
        assertEquals(0, sb.get("totalCustomerDeposit").decimalValue().add(new BigDecimal("250"))
                .compareTo(sa.get("totalCustomerDeposit").decimalValue()));
        assertEquals(0, sb.get("totalVendorDeposit").decimalValue().add(new BigDecimal("75"))
                .compareTo(sa.get("totalVendorDeposit").decimalValue()));
        assertEquals(cust, after.get("topCustomers").get(0).get("id").asText());

        assertEquals(0, before.get("agingPiutang").get("d60").decimalValue().add(new BigDecimal("1234.50"))
                .compareTo(after.get("agingPiutang").get("d60").decimalValue()));
        assertEquals(0, before.get("agingHutang").get("over90").decimalValue().add(new BigDecimal("800"))
                .compareTo(after.get("agingHutang").get("over90").decimalValue()));
        for (String k : List.of("current", "d30", "d60", "d90", "over90")) {
            assertTrue(after.get("agingPiutang").has(k) && after.get("agingHutang").has(k));
        }
        JsonNode overdue = after.get("agingPiutangInvoices");
        assertTrue(overdue.size() <= 10);
        for (JsonNode n : overdue) {
            if (si.equals(n.get("id").asText())) {
                assertEquals(45, n.get("daysOverdue").asInt());
                assertTrue(n.has("customerName") && n.has("dueDate") && n.has("invoiceNumber"));
            }
        }
        JsonNode hutangRow = after.get("agingHutangInvoices").get(0);
        assertTrue(hutangRow.has("supplierName") && hutangRow.has("daysOverdue"));

        JsonNode recent = after.get("recentPayments").get(0);
        assertEquals(pay, recent.get("id").asText());
        assertEquals("Receive", recent.get("paymentType").asText());
        assertEquals("Petty Cash", recent.get("accountName").asText());
        assertEquals(0, new BigDecimal("321").compareTo(recent.get("amount").decimalValue()));
        assertTrue(recent.get("partyName").asText().startsWith("PT Dashboard Pelanggan"));
    }

    private static <T> List<T> iterable(java.util.Iterator<T> it) {
        List<T> out = new java.util.ArrayList<>();
        it.forEachRemaining(out::add);
        return out;
    }
}
