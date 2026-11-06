package id.neravoxa.erp.toll;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.support.ErpIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jasa maklon end-to-end: STT titipan masuk buku stok titipan (stok sendiri tidak berubah) → produksi maklon
 * (bahan & hasil di stok titipan, produk samping milik pabrik ke stok sendiri, jurnal reklas 5.13/5.12) →
 * pengembalian → tagihan (ongkos proses, minimum, biaya simpan) → laporan, transfer, migrasi STT titipan lama,
 * dan baris jasa Sales Order.
 */
class TollServiceFlowTest extends ErpIntegrationTest {

    static final String RUNS = "/api/erp/inventory/production-runs";
    static final String DRAFTS = RUNS + "/drafts";

    static String uid() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    static void assertNum(String expected, Object actual) {
        assertNotNull(actual, "expected " + expected + " but was null");
        BigDecimal a = actual instanceof JsonNode n ? n.decimalValue()
                : actual instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(actual));
        assertEquals(0, new BigDecimal(expected).compareTo(a), "expected " + expected + " but was " + a);
    }

    String customer(String name) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO parties (id, name, party_type) VALUES (?, ?, 'Customer')", id, name);
        return id;
    }

    String item(String name) throws Exception {
        Result r = call(post("/api/erp/inventory/items", map("code", "TL-" + uid(), "name", name + " " + uid(), "unit", "kg")));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        return r.data().get("id").asText();
    }

    String wh() {
        return jdbc.queryForObject("SELECT id FROM warehouses WHERE code = 'GDG-UTAMA'", String.class);
    }

    String accountId(String number) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = ?", String.class, number);
    }

    BigDecimal ownStock(String itemId) {
        return jdbc.queryForObject("SELECT current_stock FROM inventory_items WHERE id = ?", BigDecimal.class, itemId);
    }

    BigDecimal ownWhStock(String itemId) {
        List<BigDecimal> r = jdbc.queryForList("SELECT current_stock FROM inventory_stocks WHERE item_id = ? AND warehouse_id = ?",
                BigDecimal.class, itemId, wh());
        return r.isEmpty() ? BigDecimal.ZERO : r.get(0);
    }

    BigDecimal titipan(String partyId, String itemId, String warehouseId) {
        List<BigDecimal> r = jdbc.queryForList("SELECT current_stock FROM customer_stocks WHERE party_id = ? AND item_id = ? "
                + "AND warehouse_id = ?", BigDecimal.class, partyId, itemId, warehouseId);
        return r.isEmpty() ? BigDecimal.ZERO : r.get(0);
    }

    BigDecimal jeSum(String entryNumber, String accountNumber, String side) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(ji." + side + "), 0) FROM journal_entry_accounts ji "
                        + "JOIN journal_entries j ON j.id = ji.journal_entry_id JOIN accounts a ON a.id = ji.account_id "
                        + "WHERE j.entry_number = ? AND a.account_number = ?",
                BigDecimal.class, entryNumber, accountNumber);
    }

    String jeStatus(String entryNumber) {
        List<String> r = jdbc.queryForList("SELECT status FROM journal_entries WHERE entry_number = ?", String.class, entryNumber);
        return r.isEmpty() ? null : r.get(0);
    }

    JsonNode titipanStt(String partyId, String tollOrderId, String itemId, Object qty, String date) throws Exception {
        Map<String, Object> body = map("date", date, "partyId", partyId, "isCustomerOwned", true,
                "items", List.of(map("inventoryItemId", itemId, "warehouseId", wh(), "itemName", "Titipan", "quantity", qty,
                        "unit", "kg")));
        if (tollOrderId != null) {
            body.put("tollOrderId", tollOrderId);
        }
        Result r = call(post("/api/erp/goods-receipts", body));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        return r.data();
    }

    JsonNode tollRun(Map<String, Object> body) throws Exception {
        Result d = call(post(RUNS, body));
        assertEquals(201, d.status(), String.valueOf(d.body()));
        String draftId = d.data().get("draft").get("id").asText();
        Result r = call(post(DRAFTS + "/" + draftId + "/post", map()));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        return r.data();
    }

    @Test
    void toll_order_full_cycle_keeps_own_stock_separate_and_bills_correctly() throws Exception {
        asAdmin();
        String u = uid();
        String cust = customer("Pelanggan Maklon " + u);
        String gkp = item("GKP");
        String beras = item("Beras");
        String dedak = item("Dedak");
        // Stok sendiri GKP 100 kg @5.000 — tidak boleh tersentuh barang titipan.
        ok(post("/api/erp/inventory/movements", map("itemId", gkp, "warehouseId", wh(), "movementType", "In",
                "quantity", 100, "unitCost", 5000, "date", today())));
        BigDecimal avgBefore = jdbc.queryForObject("SELECT average_cost FROM inventory_items WHERE id = ?", BigDecimal.class, gkp);

        // Item jasa + tarif khusus pelanggan.
        JsonNode svc = ok(post("/api/erp/service-items", map("code", "JM-" + u, "name", "Jasa Giling " + u, "unit", "kg",
                "defaultRate", 300, "accountId", accountId("4.2"), "pricingBasis", "InputQty", "minimumCharge", 500000)));
        assertEquals("InputQty", svc.get("pricingBasis").asText());
        String svcId = svc.get("id").asText();
        Result rate = call(post("/api/erp/toll/rates", map("serviceItemId", svcId, "partyId", cust, "rate", 250)));
        assertEquals(201, rate.status(), String.valueOf(rate.body()));
        JsonNode defaults = ok(get("/api/erp/toll/orders/defaults?partyId=" + cust + "&serviceItemId=" + svcId));
        assertEquals("party", defaults.get("source").asText());
        assertNum("250", defaults.get("rate"));
        assertNum("500000", defaults.get("minimumCharge"));

        // ── Order ──
        assertEquals("Pelanggan wajib dipilih.", call(post("/api/erp/toll/orders", map("date", today()))).error());
        Result orderRes = call(post("/api/erp/toll/orders", map("date", today(), "partyId", cust, "serviceItemId", svcId,
                "byProductOwner", "Factory", "expectedYieldPct", 62, "storageRate", 1)));
        assertEquals(201, orderRes.status(), String.valueOf(orderRes.body()));
        JsonNode order = orderRes.data();
        String orderId = order.get("id").asText();
        String orderNo = order.get("orderNumber").asText();
        assertTrue(orderNo.startsWith("OJM-"), orderNo);
        assertEquals("Open", order.get("status").asText());
        assertEquals("InputQty", order.get("pricingBasis").asText());
        assertNum("250", order.get("rate"));
        assertNum("500000", order.get("minimumCharge"));

        // STT biasa tidak boleh ditautkan ke order; pihak pelanggan murni bukan supplier.
        Result notOwned = call(post("/api/erp/goods-receipts", map("date", today(), "partyId", cust, "tollOrderId", orderId,
                "items", List.of(map("inventoryItemId", gkp, "warehouseId", wh(), "itemName", "X", "quantity", 5, "unit", "kg",
                        "lockedUnitCost", 1000)))));
        assertEquals(400, notOwned.status());

        // ── STT titipan → buku stok titipan, stok sendiri tetap ──
        JsonNode stt = titipanStt(cust, orderId, gkp, 1000, today());
        assertTrue(stt.get("isCustomerOwned").asBoolean());
        assertEquals(orderId, stt.get("tollOrderId").asText());
        assertNum("100", ownStock(gkp));
        assertNum("100", ownWhStock(gkp));
        assertNum(avgBefore.toPlainString(), jdbc.queryForObject("SELECT average_cost FROM inventory_items WHERE id = ?",
                BigDecimal.class, gkp));
        assertNum("1000", titipan(cust, gkp, wh()));
        assertNull(jeStatus("JV-" + stt.get("sttNumber").asText()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE reference_id = ?", Integer.class,
                stt.get("id").asText()));
        assertEquals("InProgress", ok(get("/api/erp/toll/orders/" + orderId)).get("status").asText());

        // ── Produksi maklon ──
        Map<String, Object> runBody = map("date", today(), "warehouseId", wh(), "tollOrderId", orderId, "notes", "Maklon",
                "inputs", List.of(map("itemId", gkp, "quantity", 1000)),
                "outputs", List.of(map("itemId", beras, "quantity", 600, "isByProduct", false),
                        map("itemId", dedak, "quantity", 100, "unitPrice", 2000, "isByProduct", true)),
                "costs", List.of(map("description", "Biaya giling", "costSide", "InputSide", "basis", "InputQty", "rate", 100)));
        // Draft memperingatkan berdasarkan stok titipan, bukan stok sendiri.
        Map<String, Object> tooMuch = new LinkedHashMap<>(runBody);
        tooMuch.put("inputs", List.of(map("itemId", gkp, "quantity", 1500)));
        JsonNode warnDraft = ok(post(RUNS, tooMuch));
        assertTrue(warnDraft.get("warnings").get(0).asText().startsWith("Stok titipan"), warnDraft.toString());

        JsonNode run = tollRun(runBody);
        String runId = run.get("id").asText();
        String runNo = run.get("runNumber").asText();
        assertEquals(orderId, run.get("tollOrderId").asText());
        assertNum("100", ownStock(gkp));
        assertNum("0", titipan(cust, gkp, wh()));
        assertNum("600", titipan(cust, beras, wh()));
        assertNum("0", ownStock(beras));
        assertNum("100", ownStock(dedak));
        assertNum("2000", jdbc.queryForObject("SELECT average_cost FROM inventory_items WHERE id = ?", BigDecimal.class, dedak));
        // Jurnal: produk samping milik pabrik (DR persediaan / CR pendapatan jasa) + reklas biaya konversi.
        String jv = "JV-" + runNo;
        assertEquals("Submitted", jeStatus(jv));
        assertNum("200000", jeSum(jv, "1.4.0", "debit"));
        assertNum("200000", jeSum(jv, "4.2", "credit"));
        assertNum("100000", jeSum(jv, "5.13", "debit"));
        assertNum("100000", jeSum(jv, "5.12", "credit"));
        assertNum("0", jeSum(jv, "1.4.0", "credit"));
        JsonNode runDetail = ok(get(RUNS + "/" + runId));
        assertEquals(orderNo, runDetail.get("tollOrder").get("orderNumber").asText());

        // Stok titipan kurang → posting ditolak (pesan menyebut stok titipan).
        Result shortDraft = call(post(RUNS, map("date", today(), "warehouseId", wh(), "tollOrderId", orderId,
                "inputs", List.of(map("itemId", gkp, "quantity", 10)),
                "outputs", List.of(map("itemId", beras, "quantity", 6, "isByProduct", false)))));
        Result shortPost = call(post(DRAFTS + "/" + shortDraft.data().get("draft").get("id").asText() + "/post", map()));
        assertEquals(400, shortPost.status());
        assertTrue(shortPost.error().startsWith("Stok titipan '"), shortPost.error());

        // STT titipan yang sudah diproses tidak bisa dibatalkan.
        Result sttCancel = call(post("/api/erp/goods-receipts/" + stt.get("id").asText() + "/cancel", map()));
        assertEquals(400, sttCancel.status());
        assertTrue(sttCancel.error().contains("sudah terpakai"), sttCancel.error());

        JsonNode detail = ok(get("/api/erp/toll/orders/" + orderId));
        JsonNode prod = detail.get("production");
        assertNum("1000", prod.get("inputQty"));
        assertNum("600", prod.get("mainOutputQty"));
        assertNum("100", prod.get("byProductQty"));
        assertNum("300", prod.get("shrinkQty"));
        assertNum("60", prod.get("yieldPct"));
        assertNum("-2", prod.get("yieldVariancePct"));
        assertEquals(1, detail.get("factoryByProducts").size());

        // ── Pengembalian hasil ──
        Result over = call(post("/api/erp/toll/returns", map("date", today(), "partyId", cust, "tollOrderId", orderId,
                "items", List.of(map("itemId", beras, "warehouseId", wh(), "quantity", 700)))));
        assertEquals(400, over.status());
        Result retRes = call(post("/api/erp/toll/returns", map("date", today(), "partyId", cust, "tollOrderId", orderId,
                "nomorMobil", "B 1 TL", "items", List.of(map("itemId", beras, "warehouseId", wh(), "quantity", 600)))));
        assertEquals(201, retRes.status(), String.valueOf(retRes.body()));
        JsonNode ret = retRes.data();
        assertTrue(ret.get("returnNumber").asText().startsWith("SJT-"));
        assertEquals(1, ret.get("items").size());
        assertNum("0", titipan(cust, beras, wh()));

        // Hasil sudah dikembalikan → run tidak bisa dibatalkan sebelum pengembaliannya dibatalkan.
        Result runCancel = call(put(RUNS + "/" + runId + "/cancel", map()));
        assertEquals(400, runCancel.status());
        assertTrue(runCancel.error().contains("sudah terpakai"), runCancel.error());
        JsonNode retCancelled = ok(post("/api/erp/toll/returns/" + ret.get("id").asText() + "/cancel", map()));
        assertEquals("Cancelled", retCancelled.get("status").asText());
        assertNum("600", titipan(cust, beras, wh()));
        ok(post("/api/erp/toll/returns", map("date", today(), "partyId", cust, "tollOrderId", orderId,
                "items", List.of(map("itemId", beras, "warehouseId", wh(), "quantity", 600)))));

        // ── Tagihan ──
        JsonNode bill = ok(get("/api/erp/toll/orders/" + orderId + "/billing"));
        assertNum("1000", bill.get("basisQty"));
        assertNum("1000", bill.get("unbilledQty"));
        assertNum("250000", bill.get("unbilledServiceAmount"));
        assertNum("0", bill.get("minimumTopUp"));
        assertEquals(1, bill.get("lines").size());
        JsonNode inv = ok(post("/api/erp/toll/orders/" + orderId + "/invoice", map("date", today())));
        assertNum("250000", inv.get("grandTotal"));
        assertEquals(orderId, inv.get("items").get(0).get("tollOrderId").asText());
        assertEquals("Service", inv.get("items").get(0).get("tollChargeType").asText());
        assertNum("250000", jeSum("JV-" + inv.get("invoiceNumber").asText(), "4.2", "credit"));
        assertEquals("Tidak ada tagihan yang belum ditagihkan untuk " + orderNo + ".",
                call(post("/api/erp/toll/orders/" + orderId + "/invoice", map("date", today()))).error());

        // Order selesai → ongkos minimum ditagih selisihnya.
        String cancelErr = call(post("/api/erp/toll/orders/" + orderId + "/cancel", map())).error();
        assertTrue(cancelErr.startsWith("Tidak bisa membatalkan order " + orderNo + ": masih ada"), cancelErr);
        JsonNode completed = ok(post("/api/erp/toll/orders/" + orderId + "/complete", map()));
        assertEquals("Completed", completed.get("status").asText());
        assertNum("250000", completed.get("billing").get("minimumTopUp"));
        JsonNode inv2 = ok(post("/api/erp/toll/orders/" + orderId + "/invoice", map("date", today())));
        assertNum("250000", inv2.get("grandTotal"));
        assertEquals("Minimum", inv2.get("items").get(0).get("tollChargeType").asText());
        JsonNode after = ok(get("/api/erp/toll/orders/" + orderId + "/billing"));
        assertNum("0", after.get("unbilledTotal"));
        assertNum("500000", after.get("billedTotal"));

        // Order selesai tidak menerima STT baru.
        Result closed = call(post("/api/erp/goods-receipts", map("date", today(), "partyId", cust, "isCustomerOwned", true,
                "tollOrderId", orderId, "items", List.of(map("inventoryItemId", gkp, "warehouseId", wh(), "itemName", "X",
                        "quantity", 5, "unit", "kg")))));
        assertEquals(400, closed.status());
        assertTrue(closed.error().contains("sudah Selesai"), closed.error());

        // Daftar & laporan.
        JsonNode list = ok(get("/api/erp/toll/orders?partyId=" + cust));
        assertEquals(1, list.get("total").asInt());
        assertNum("1000", list.get("data").get(0).get("processedQty"));
        assertNum("500000", list.get("data").get(0).get("billedAmount"));
        JsonNode yield = ok(get("/api/erp/toll/reports/yield?partyId=" + cust));
        assertNum("60", yield.get("orders").get(0).get("yieldPct"));
        assertNum("1000", yield.get("totals").get("inputQty"));
        JsonNode card = ok(get("/api/erp/toll/stock/card?partyId=" + cust + "&itemId=" + gkp));
        assertEquals(2, card.get("rows").size());
        assertNum("1000", card.get("totalIn"));
        assertNum("0", card.get("closing"));
    }

    @Test
    void storage_fee_transfer_and_customer_owned_by_products() throws Exception {
        asAdmin();
        String u = uid();
        String cust = customer("Pelanggan Simpan " + u);
        String gkp = item("GKP");
        String beras = item("Beras");
        String sekam = item("Sekam");
        JsonNode order = ok(post("/api/erp/toll/orders", map("date", today(), "partyId", cust, "pricingBasis", "OutputQty",
                "rate", 400, "storageRate", 2, "storageFreeDays", 1)));
        String orderId = order.get("id").asText();
        assertEquals("Customer", order.get("byProductOwner").asText());

        LocalDate received = LocalDate.now(ZoneOffset.UTC).minusDays(5);
        titipanStt(cust, orderId, gkp, 100, received.toString());
        long days = ChronoUnit.DAYS.between(received, ErpDates.businessToday());
        long chargeable = Math.max(0, days - 1);
        JsonNode bill = ok(get("/api/erp/toll/orders/" + orderId + "/billing"));
        assertNum(String.valueOf(100 * chargeable), bill.get("storage").get("qtyDays"));
        assertNum(String.valueOf(200 * chargeable), bill.get("storage").get("fee"));

        // Produk samping milik pelanggan → stok titipan, bukan stok sendiri; tanpa jurnal.
        JsonNode run = tollRun(map("date", today(), "warehouseId", wh(), "tollOrderId", orderId,
                "inputs", List.of(map("itemId", gkp, "quantity", 100)),
                "outputs", List.of(map("itemId", beras, "quantity", 65, "isByProduct", false),
                        map("itemId", sekam, "quantity", 20, "unitPrice", 0, "isByProduct", true))));
        assertNum("20", titipan(cust, sekam, wh()));
        assertNum("0", ownStock(sekam));
        assertNull(jeStatus("JV-" + run.get("runNumber").asText()));
        JsonNode ob = ok(get("/api/erp/toll/orders/" + orderId + "/billing"));
        assertNum("65", ob.get("basisQty"));
        assertNum("26000", ob.get("unbilledServiceAmount"));

        // Transfer stok titipan ke gudang lain lalu dibatalkan.
        Result whRes = call(post("/api/erp/warehouses", map("code", "TTP" + u, "name", "Gudang Titipan " + u)));
        assertEquals(201, whRes.status(), String.valueOf(whRes.body()));
        String wh2 = whRes.data().get("id").asText();
        JsonNode trf = ok(post("/api/erp/toll/stock/transfer", map("date", today(), "partyId", cust, "itemId", beras,
                "fromWarehouseId", wh(), "toWarehouseId", wh2, "quantity", 40, "tollOrderId", orderId)));
        assertNum("25", titipan(cust, beras, wh()));
        assertNum("40", titipan(cust, beras, wh2));
        Result tooMuch = call(post("/api/erp/toll/stock/transfer", map("date", today(), "partyId", cust, "itemId", beras,
                "fromWarehouseId", wh(), "toWarehouseId", wh2, "quantity", 30)));
        assertEquals(400, tooMuch.status());
        assertTrue(tooMuch.error().contains("Ada di gudang lain"), tooMuch.error());
        ok(post("/api/erp/toll/stock/transfer/" + trf.get("id").asText() + "/cancel", map()));
        assertNum("65", titipan(cust, beras, wh()));
        assertNum("0", titipan(cust, beras, wh2));

        JsonNode balances = ok(get("/api/erp/toll/stock/balances?partyId=" + cust));
        assertEquals(2, balances.get("data").size());
        JsonNode storage = ok(get("/api/erp/toll/reports/storage?partyId=" + cust + "&startDate=" + received.minusDays(1)
                + "&endDate=" + today()));
        assertEquals(1, storage.get("data").size());

        // Order tanpa dokumen bisa dibatalkan; dengan dokumen ditolak.
        JsonNode empty = ok(post("/api/erp/toll/orders", map("date", today(), "partyId", cust, "rate", 1)));
        assertEquals("Cancelled", ok(post("/api/erp/toll/orders/" + empty.get("id").asText() + "/cancel", map()))
                .get("status").asText());
        assertEquals(400, call(post("/api/erp/toll/orders/" + orderId + "/cancel", map())).status());
    }

    @Test
    void legacy_customer_owned_receipt_is_migrated_out_of_own_stock() throws Exception {
        asAdmin();
        String u = uid();
        String cust = customer("Pelanggan Lama " + u);
        String gkp = item("GKP Lama");
        String fy = jdbc.queryForObject("SELECT id FROM fiscal_years WHERE start_date <= now() AND end_date >= now() "
                + "AND is_closed = false ORDER BY start_date DESC LIMIT 1", String.class);
        String user = jdbc.queryForObject("SELECT id FROM users LIMIT 1", String.class);
        String gr = UUID.randomUUID().toString();
        String line = UUID.randomUUID().toString();
        String sm = UUID.randomUUID().toString();
        // Perilaku lama: STT titipan menambah stok sendiri dengan nilai 0.
        jdbc.update("INSERT INTO goods_receipts (id, stt_number, date, party_id, is_customer_owned, status, fiscal_year_id, created_by) "
                + "VALUES (?, ?, now(), ?, TRUE, 'Submitted', ?, ?)", gr, "STT-LEGACY-" + u, cust, fy, user);
        jdbc.update("INSERT INTO stock_movements (id, movement_number, date, item_id, warehouse_id, movement_type, quantity, unit_cost, "
                + "total_value, reference_type, reference_id, fiscal_year_id, created_by_id) VALUES (?, ?, now(), ?, ?, 'In', 70, 0, 0, "
                + "'GoodsReceipt', ?, ?, ?)", sm, "SM-LEGACY-" + u, gkp, wh(), gr, fy, user);
        jdbc.update("INSERT INTO goods_receipt_items (id, goods_receipt_id, inventory_item_id, warehouse_id, item_name, quantity, unit, "
                + "stock_movement_id) VALUES (?, ?, ?, ?, 'GKP', 70, 'kg', ?)", line, gr, gkp, wh(), sm);
        jdbc.update("UPDATE inventory_items SET current_stock = current_stock + 70 WHERE id = ?", gkp);
        jdbc.update("INSERT INTO inventory_stocks (id, item_id, warehouse_id, current_stock) VALUES (?, ?, ?, 70)",
                UUID.randomUUID().toString(), gkp, wh());
        assertNum("70", ownStock(gkp));

        String script = new String(new ClassPathResource("db/erp/06_toll_service.sql").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        jdbc.execute(script);
        jdbc.execute(script); // idempoten

        assertNum("0", ownStock(gkp));
        assertNum("0", ownWhStock(gkp));
        assertNum("70", titipan(cust, gkp, wh()));
        assertTrue(jdbc.queryForObject("SELECT is_cancelled FROM stock_movements WHERE id = ?", Boolean.class, sm));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM customer_stock_movements WHERE source_line_id = ?",
                Integer.class, line));

        // STT lama dibatalkan lewat jalur titipan (buku stok titipan dibalik).
        JsonNode cancelled = ok(post("/api/erp/goods-receipts/" + gr + "/cancel", map()));
        assertEquals("Cancelled", cancelled.get("status").asText());
        assertNum("0", titipan(cust, gkp, wh()));
        assertNum("0", ownStock(gkp));
    }

    @Test
    void sales_order_service_lines_are_invoiced_not_delivered() throws Exception {
        asAdmin();
        String u = uid();
        String cust = customer("Pelanggan SO Jasa " + u);
        String goods = item("Beras SO");
        ok(post("/api/erp/inventory/movements", map("itemId", goods, "warehouseId", wh(), "movementType", "In",
                "quantity", 50, "unitCost", 10000, "date", today())));
        JsonNode so = ok(post("/api/erp/sales-orders", map("date", today(), "partyId", cust, "items", List.of(
                map("inventoryItemId", goods, "itemName", "Beras", "quantity", 10, "unit", "kg", "rate", 12000, "amount", 120000),
                map("itemType", "service", "itemName", "Jasa giling", "quantity", 2, "unit", "Jasa", "rate", 50000,
                        "amount", 100000)))));
        String soId = so.get("id").asText();
        ok(post("/api/erp/sales-orders/" + soId + "/submit", map()));
        JsonNode detail = ok(get("/api/erp/sales-orders/" + soId));
        JsonNode goodsLine = detail.get("items").get(0);
        JsonNode svcLine = detail.get("items").get(1);
        assertEquals("service", svcLine.get("itemType").asText());
        assertTrue(svcLine.get("inventoryItemId").isNull());
        assertNum("0", svcLine.get("invoicedQty"));
        assertFalse(goodsLine.has("invoicedQty"));

        Result doSvc = call(post("/api/erp/sales-orders/" + soId + "/generate-do", map("date", today(),
                "items", List.of(map("salesOrderItemId", svcLine.get("id").asText(), "quantity", 1, "warehouseId", wh())))));
        assertEquals(400, doSvc.status());
        assertTrue(doSvc.error().startsWith("Baris jasa 'Jasa giling'"), doSvc.error());

        // Barang dikirim penuh → SO FullyDelivered walau baris jasa tidak dikirim.
        ok(post("/api/erp/sales-orders/" + soId + "/generate-do", map("date", today(),
                "items", List.of(map("salesOrderItemId", goodsLine.get("id").asText(), "quantity", 10, "warehouseId", wh())))));
        assertEquals("FullyDelivered", ok(get("/api/erp/sales-orders/" + soId)).get("status").asText());

        // Baris jasa ditagih langsung; tidak boleh melebihi qty SO.
        ok(post("/api/erp/sales/invoices", map("date", today(), "partyId", cust, "items", List.of(
                map("itemName", "Jasa giling", "itemType", "service", "quantity", 2, "unit", "Jasa", "rate", 50000,
                        "salesOrderItemId", svcLine.get("id").asText())))));
        assertNum("2", ok(get("/api/erp/sales-orders/" + soId)).get("items").get(1).get("invoicedQty"));
        Result again = call(post("/api/erp/sales/invoices", map("date", today(), "partyId", cust, "items", List.of(
                map("itemName", "Jasa giling", "itemType", "service", "quantity", 1, "unit", "Jasa", "rate", 50000,
                        "salesOrderItemId", svcLine.get("id").asText())))));
        assertEquals(400, again.status());
        assertTrue(again.error().contains("melebihi sisa"), again.error());
    }
}
