package id.neravoxa.erp.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gerakan stok manual: stok per gudang + total, WAC, GL seimbang, edit, pembatalan + replay WAC. */
class StockMovementFlowTest extends InventoryTestSupport {

    @Test
    void stock_in_out_edit_cancel_keep_stock_wac_and_gl_consistent() throws Exception {
        asAdmin();
        JsonNode item = createItem("Beras Uji Gerak", "Kg");
        String itemId = item.get("id").asText();
        String gdg = defaultWarehouseId();
        String whB = createWarehouse("Gudang Uji B");

        // 1) Stok masuk tanpa gudang → gudang default; GL DR Persediaan / CR Ekuitas Saldo Awal.
        JsonNode in1 = movement(itemId, null, "In", "10", "1000", "stok awal");
        assertEquals(gdg, in1.get("warehouseId").asText());
        assertTrue(in1.get("movementNumber").asText().startsWith("SM-"));
        assertEquals(itemId, in1.get("item").get("id").asText());
        assertFalse(in1.has("warehouse"), "relasi yang tidak di-include tidak boleh muncul");
        assertNum("10000", in1.get("totalValue"));
        assertNum("10", itemStock(itemId));
        assertNum("10", whStock(itemId, gdg));
        assertNum("1000", itemAvg(itemId));
        String je1 = in1.get("journalEntryId").asText();
        assertJournalBalanced(je1);
        List<Map<String, Object>> lines = journalLines(je1);
        assertEquals("1.4.0", lines.get(0).get("acc"));
        assertEquals("3.1", lines.get(1).get("acc"));
        assertEquals("Stok Masuk: Beras Uji Gerak 10 Kg - stok awal", lines.get(0).get("description"));
        assertEquals(accountId("3.1"), in1.get("offsetAccountId").asText());

        // 2) Stok masuk ke gudang B dengan harga lain → WAC = (10×1000 + 10×2000) / 20 = 1500.
        JsonNode in2 = movement(itemId, whB, "In", "10", "2000", null);
        assertNum("20", itemStock(itemId));
        assertNum("10", whStock(itemId, whB));
        assertNum("1500", itemAvg(itemId));

        // 3) Stok keluar melebihi stok gudang default (10) → ditolak dengan pesan ENTARIS.
        Map<String, Object> tooMuch = new LinkedHashMap<>();
        tooMuch.put("itemId", itemId);
        tooMuch.put("warehouseId", gdg);
        tooMuch.put("movementType", "Out");
        tooMuch.put("quantity", 15);
        tooMuch.put("date", today());
        Result r = call(post("/api/erp/inventory/movements", tooMuch));
        assertEquals(400, r.status());
        assertEquals("Stok di Gudang Utama tidak cukup. Tersedia: 10 Kg", r.error());

        // 4) Stok keluar 4 @1500 → CR Persediaan.
        JsonNode out = movement(itemId, gdg, "Out", "4", "1500", null);
        assertNum("16", itemStock(itemId));
        assertNum("6", whStock(itemId, gdg));
        String jeOut = out.get("journalEntryId").asText();
        assertJournalBalanced(jeOut);
        assertEquals("3.1", journalLines(jeOut).get(0).get("acc"));
        assertBalanceMatchesLedger("1.4.0");

        // 5) Batalkan stok masuk gudang B → stok dibalik, jurnal batal, WAC di-replay (tinggal In 10@1000).
        JsonNode cancelled = ok(put("/api/erp/inventory/movements/" + in2.get("id").asText() + "/cancel", Map.of()));
        assertEquals("Gerakan stok dibatalkan.", cancelled.get("message").asText());
        assertNum("6", itemStock(itemId));
        assertNum("0", whStock(itemId, whB));
        assertEquals("Cancelled", journalStatus(in2.get("journalEntryId").asText()));
        assertNum("1000", itemAvg(itemId));
        assertBalanceMatchesLedger("1.4.0");
        assertBalanceMatchesLedger("3.1");

        Result again = call(put("/api/erp/inventory/movements/" + in2.get("id").asText() + "/cancel", Map.of()));
        assertEquals(400, again.status());
        assertEquals("Gerakan stok sudah dibatalkan.", again.error());

        // 6) Edit gerakan keluar 4 → 5: stok dibalik lalu diterapkan ulang di gudang yang sama, jurnal "(Edit)".
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("movementType", "Out");
        edit.put("quantity", 5);
        edit.put("unitCost", 1000);
        edit.put("date", today());
        JsonNode edited = ok(put("/api/erp/inventory/movements/" + out.get("id").asText(), edit));
        assertNum("5", edited.get("quantity"));
        assertNum("5", itemStock(itemId));
        assertNum("5", whStock(itemId, gdg));
        assertEquals("Cancelled", journalStatus(jeOut));
        String jeEdit = edited.get("journalEntryId").asText();
        assertJournalBalanced(jeEdit);
        assertEquals("Stok Keluar: Beras Uji Gerak 5 Kg (Edit)", journalLines(jeEdit).get(0).get("description"));
        assertBalanceMatchesLedger("1.4.0");
        assertNum("1000", itemAvg(itemId));

        // 7) GET /:id, daftar (filter item) dan KPI ringkasan.
        JsonNode one = ok(get("/api/erp/inventory/movements/" + out.get("id").asText()));
        assertEquals("Out", one.get("movementType").asText());
        assertNotNull(one.get("offsetAccount").get("accountNumber"));
        JsonNode list = ok(get("/api/erp/inventory/movements?itemId=" + itemId + "&sort=-date"));
        assertEquals(3, list.get("total").asInt());
        assertEquals(1, list.get("page").asInt());
        assertEquals("Gudang Utama", findById(list.get("data"), in1.get("id").asText()).get("warehouse").get("name").asText());
        JsonNode active = ok(get("/api/erp/inventory/movements?itemId=" + itemId + "&status=active"));
        assertEquals(2, active.get("total").asInt());
        JsonNode summary = ok(get("/api/erp/inventory/movements/summary?itemId=" + itemId));
        assertEquals(3, summary.get("count").asInt());
        assertEquals(1, summary.get("cancelledCount").asInt());
        assertNum("10", summary.get("inQty"));
        assertNum("5", summary.get("outQty"));
        Result badType = call(get("/api/erp/inventory/movements?movementType=Masuk"));
        assertEquals(400, badType.status());
        assertEquals("Tipe gerakan stok tidak dikenal.", badType.error());

        // 8) Batch picker: batch masuk aktif dengan sisa (FIFO menyerap keluar tak terlacak).
        JsonNode batches = ok(get("/api/erp/inventory/items/" + itemId + "/batches"));
        assertNum("5", batches.get("currentStock"));
        assertEquals(1, batches.get("data").size());
        assertNum("5", batches.get("data").get(0).get("qtyAvailable"));
        assertEquals("Other", batches.get("data").get(0).get("sourceType").asText());
    }

    @Test
    void cancel_requires_doc_cancel_and_validation_messages_are_copied() throws Exception {
        asAdmin();
        JsonNode item = createItem("Gabah Uji Cap", "Kg");
        JsonNode in = movement(item.get("id").asText(), null, "AdjustmentIn", "3", "0", null);
        // totalValue 0 → tanpa jurnal.
        assertTrue(in.get("journalEntryId").isNull());

        asUser("staf.produksi@test.local", "ERP_PRODUCTION");
        assertEquals(200, call(get("/api/erp/inventory/movements?limit=1")).status());
        Result denied = call(put("/api/erp/inventory/movements/" + in.get("id").asText() + "/cancel", Map.of()));
        assertEquals(403, denied.status());

        asAdmin();
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("itemId", item.get("id").asText());
        bad.put("movementType", "In");
        bad.put("quantity", 0);
        bad.put("date", today());
        Result invalid = call(post("/api/erp/inventory/movements", bad));
        assertEquals(400, invalid.status());
        assertEquals("Kuantitas harus lebih dari 0.", invalid.error());

        // Kunci gerakan dokumen: gerakan milik STT / produksi tidak boleh diedit/dibatalkan per baris.
        Map<String, Object> stt = new LinkedHashMap<>();
        stt.put("itemId", item.get("id").asText());
        stt.put("movementType", "In");
        stt.put("quantity", 1);
        stt.put("date", today());
        stt.put("referenceType", "GoodsReceipt");
        JsonNode sttMov = ok(post("/api/erp/inventory/movements", stt));
        Result lockCancel = call(put("/api/erp/inventory/movements/" + sttMov.get("id").asText() + "/cancel", Map.of()));
        assertEquals("Gerakan stok dari STT tidak dapat dibatalkan per baris. Batalkan dokumennya dari halaman STT.",
                lockCancel.error());
        Map<String, Object> editBody = new LinkedHashMap<>();
        editBody.put("movementType", "In");
        editBody.put("quantity", 2);
        editBody.put("date", today());
        Result lockEdit = call(put("/api/erp/inventory/movements/" + sttMov.get("id").asText(), editBody));
        assertEquals("Gerakan stok dari STT tidak dapat diedit per baris. Ubah atau batalkan dokumennya dari halaman STT.",
                lockEdit.error());
        stt.put("referenceType", "ProductionRun");
        JsonNode prMov = ok(post("/api/erp/inventory/movements", stt));
        Result prEdit = call(put("/api/erp/inventory/movements/" + prMov.get("id").asText(), editBody));
        assertEquals("Gerakan stok dari proses produksi tidak dapat diedit secara langsung.", prEdit.error());

        // Pembalikan stok masuk yang sudah terpakai → ditolak (stok item: 3 + 1 + 1 − 4 = 1 < 3).
        movement(item.get("id").asText(), null, "Out", "4", null, null);
        Result cannot = call(put("/api/erp/inventory/movements/" + in.get("id").asText() + "/cancel", Map.of()));
        assertEquals(400, cannot.status());
        assertEquals("Tidak dapat membatalkan: stok tidak mencukupi untuk pembalikan.", cannot.error());
    }

    @Test
    void inventory_dashboard_endpoints_respond() throws Exception {
        asAdmin();
        JsonNode item = createItem("Item Dashboard", "Sak");
        movement(item.get("id").asText(), null, "In", "4", "2500", null);

        JsonNode metrics = ok(get("/api/erp/inventory/dashboard/metrics"));
        assertTrue(metrics.get("totalItems").asInt() >= 1);
        assertTrue(metrics.get("movementsThisMonth").asInt() >= 1);
        assertTrue(metrics.has("inventoryValue"));
        assertTrue(ok(get("/api/erp/inventory/dashboard/by-category")).isArray());
        JsonNode byCat = ok(get("/api/erp/inventory/dashboard/stock-value"));
        assertEquals("category", byCat.get("groupBy").asText());
        JsonNode byWh = ok(get("/api/erp/inventory/dashboard/stock-value?groupBy=warehouse"));
        assertEquals("warehouse", byWh.get("groupBy").asText());
        Result bad = call(get("/api/erp/inventory/dashboard/stock-value?groupBy=item"));
        assertEquals(400, bad.status());
        assertEquals("Pengelompokan harus category atau warehouse.", bad.error());
        assertEquals(6, ok(get("/api/erp/inventory/dashboard/movement-trend")).size());
        JsonNode top = ok(get("/api/erp/inventory/dashboard/top-items"));
        assertTrue(top.size() >= 1 && top.get(0).has("stockValue"));
        JsonNode recent = ok(get("/api/erp/inventory/dashboard/recent-movements"));
        assertTrue(recent.size() >= 1 && recent.get(0).get("item").has("code"));
        assertTrue(ok(get("/api/erp/inventory/dashboard/production-stats")).has("avgYield"));
        JsonNode prod = ok(get("/api/erp/inventory/dashboard/production?startDate=2026-01-01&endDate=2026-12-31"));
        assertTrue(prod.get("summary").has("totalRuns"));
        assertTrue(prod.get("loadedHpp").has("perProduct"));
        assertEquals(6, ok(get("/api/erp/inventory/dashboard/yield-trend")).get("months").size());
        assertTrue(ok(get("/api/erp/inventory/dashboard/oee")).get("summary").has("avgOee"));
    }

    @Test
    void stock_in_settles_cogs_backfill_and_edit_cancel_reverse_it() throws Exception {
        asAdmin();
        JsonNode item = createItem("Beras HPP Uji", "Kg");
        String itemId = item.get("id").asText();
        String userId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'erp.admin@test.local'", String.class);
        String fyId = jdbc.queryForObject("SELECT id FROM fiscal_years WHERE is_closed = false AND start_date <= now() "
                + "AND end_date >= now() ORDER BY start_date LIMIT 1", String.class);
        // Antrian koreksi HPP: 5 Kg terjual saat stok kosong dengan HPP 800.
        String partyId = jdbc.queryForObject("INSERT INTO parties (name, party_type) VALUES ('Pelanggan HPP Uji', "
                + "'Customer') RETURNING id", String.class);
        String invId = jdbc.queryForObject("INSERT INTO sales_invoices (invoice_number, date, party_id, status, grand_total, "
                + "outstanding, fiscal_year_id, created_by) VALUES (?, now(), ?, 'Submitted', 0, 0, ?, ?) RETURNING id",
                String.class, uniq("SI-UJI"), partyId, fyId, userId);
        String invItemId = jdbc.queryForObject("INSERT INTO sales_invoice_items (sales_invoice_id, inventory_item_id, "
                + "item_name, quantity, unit, rate, amount, account_id) VALUES (?, ?, 'Beras', 5, 'Kg', 0, 0, ?) RETURNING id",
                String.class, invId, itemId, accountId("4.1"));
        String queueId = jdbc.queryForObject("INSERT INTO cogs_backfill_queue (sales_invoice_id, sales_invoice_item_id, "
                + "inventory_item_id, qty_pending, qty_original, cost_at_sale, status, fiscal_year_id) "
                + "VALUES (?, ?, ?, 5, 5, 800, 'Pending', ?) RETURNING id", String.class, invId, invItemId, itemId, fyId);

        // Stok masuk 10 @1000 → selisih 5 × (1000 − 800) = 1000: DR HPP / CR Persediaan, antrian Settled.
        JsonNode in = movement(itemId, null, "In", "10", "1000", null);
        String movId = in.get("id").asText();
        Map<String, Object> q = jdbc.queryForMap("SELECT status, qty_pending FROM cogs_backfill_queue WHERE id = ?", queueId);
        assertEquals("Settled", q.get("status"));
        String settleJe = jdbc.queryForObject("SELECT journal_entry_id FROM cogs_backfill_settlements WHERE queue_id = ? "
                + "AND trigger_source = 'StockMovement' AND trigger_ref_id = ?", String.class, queueId, movId);
        assertJournalBalanced(settleJe);
        assertNum("1000", jdbc.queryForObject("SELECT SUM(debit) FROM journal_entry_accounts ji JOIN accounts a "
                + "ON a.id = ji.account_id WHERE ji.journal_entry_id = ? AND a.account_number = '5.1'", java.math.BigDecimal.class, settleJe));
        assertNum("900", itemAvg(itemId)); // (10×1000 − 1000) / 10

        // Edit 10@1000 → 8@1100: auto-settle lama dibalik, settle ulang 5 × 300 = 1500, WAC di-replay.
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("movementType", "In");
        edit.put("quantity", 8);
        edit.put("unitCost", 1100);
        edit.put("date", today());
        ok(put("/api/erp/inventory/movements/" + movId, edit));
        assertEquals("Cancelled", journalStatus(settleJe));
        String settleJe2 = jdbc.queryForObject("SELECT journal_entry_id FROM cogs_backfill_settlements WHERE queue_id = ?",
                String.class, queueId);
        assertNum("1500", jdbc.queryForObject("SELECT SUM(debit) FROM journal_entry_accounts WHERE journal_entry_id = ?",
                java.math.BigDecimal.class, settleJe2));
        assertNum("8", itemStock(itemId));
        assertNum("1100", itemAvg(itemId));

        // Batal → settlement dibalik: jurnal batal, antrian kembali Pending penuh, baris settlement dihapus.
        ok(put("/api/erp/inventory/movements/" + movId + "/cancel", Map.of()));
        assertEquals("Cancelled", journalStatus(settleJe2));
        q = jdbc.queryForMap("SELECT status, qty_pending FROM cogs_backfill_queue WHERE id = ?", queueId);
        assertEquals("Pending", q.get("status"));
        assertNum("5", (java.math.BigDecimal) q.get("qty_pending"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM cogs_backfill_settlements WHERE queue_id = ?",
                Integer.class, queueId));
        assertNum("0", itemStock(itemId));
        assertBalanceMatchesLedger("1.4.0");
        assertBalanceMatchesLedger("5.1");
    }

    private static JsonNode findById(JsonNode arr, String id) {
        for (JsonNode n : arr) {
            if (id.equals(n.get("id").asText())) {
                return n;
            }
        }
        throw new AssertionError("id " + id + " tidak ada di " + arr);
    }
}
