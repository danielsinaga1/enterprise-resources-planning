package id.neravoxa.erp.production;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Alur Data Produksi end-to-end: draft → posting (stok keluar bahan, stok masuk hasil, WAC, jurnal
 * seimbang, HPP loaded, OEE), detail/daftar/ringkasan, edit langsung (replay stok/WAC + jurnal revisi),
 * pembatalan (balik stok + jurnal) dan guard-guard-nya.
 */
class ProductionRunFlowTest extends ProductionTestSupport {

    @Test
    void submit_posts_stock_wac_hpp_oee_and_balanced_journal() throws Exception {
        asAdmin();
        Scenario s = scenario();
        JsonNode created = createDraft(standardBody(s));
        JsonNode draft = created.get("draft");
        assertEquals("Draft", draft.get("status").asText());
        assertTrue(draft.get("draftNumber").asText().startsWith("DPR-"));
        assertNum("1000", draft.get("summary").get("inputQty"));
        assertNum("800", draft.get("summary").get("outputQty"));
        assertEquals(itemName(s.beras()), draft.get("summary").get("mainOutput").asText());
        assertEquals(0, created.get("warnings").size());
        // Draft tanpa efek stok.
        assertNum("1000", stock(s.gkp()));

        Result posted = call(post(DRAFTS + "/" + draft.get("id").asText() + "/post", map()));
        assertEquals(201, posted.status(), String.valueOf(posted.body()));
        JsonNode run = posted.data();
        String runId = run.get("id").asText();
        String runNumber = run.get("runNumber").asText();
        assertTrue(runNumber.startsWith("PR-"));
        assertEquals(3, run.get("items").size());
        assertTrue(run.get("items").get(0).has("item"));
        assertNum("60", run.get("yieldPct"));
        // HPP loaded = (5.000.000 + 100.000 + 30.000 − 200.000) / 600
        assertNum("8216.6667", run.get("computedHpp"));
        assertNum("5000000", run.get("totalMaterialCost"));
        assertNum("100000", run.get("totalCostInputSide"));
        assertNum("30000", run.get("totalCostOutputSide"));
        assertNum("200000", run.get("totalByproductValue"));
        assertFalse(run.get("hppManualOverride").asBoolean());
        // OEE: planned 420 menit, run 390, ideal 650 kg, quality 80%.
        assertNum("92.86", run.get("availabilityPct"));
        assertNum("92.31", run.get("performancePct"));
        assertNum("80", run.get("qualityPct"));
        assertNum("68.57", run.get("oeePct"));
        assertNum("30", run.get("downtimeMin"));

        assertNum("0", stock(s.gkp()));
        assertNum("600", stock(s.beras()));
        assertNum("200", stock(s.sekam()));
        assertNum("0", whStock(s.gkp()));
        assertNum("600", whStock(s.beras()));
        assertNum("5000", avg(s.gkp()));
        assertNum("8216.67", avg(s.beras()));
        assertNum("1000", avg(s.sekam()));
        assertEquals(3, activeMovements(runId));
        Map<String, Object> berasMov = jdbc.queryForMap("SELECT unit_cost, total_value, unit_cost_loaded, movement_type "
                + "FROM stock_movements WHERE reference_id = ? AND item_id = ?", runId, s.beras());
        assertEquals("In", berasMov.get("movement_type"));
        assertNum("8216.67", (BigDecimal) berasMov.get("unit_cost"));
        assertNum("4930000.02", (BigDecimal) berasMov.get("total_value"));
        assertNum("8216.6667", (BigDecimal) berasMov.get("unit_cost_loaded"));

        String jv = "JV-" + runNumber;
        assertEquals("Submitted", jeStatus(jv));
        BigDecimal[] t = jeTotals(jv);
        assertNum("5130000.02", t[0]);
        assertNum("5130000.02", t[1]);
        assertNum("5130000.02", t[2]);
        assertNum("5130000.02", t[3]);
        assertNum("5000000", jeSum(jv, "1.4.0", "credit"));
        assertNum("5130000.02", jeSum(jv, "1.4.0", "debit"));
        assertNum("130000", jeSum(jv, "5.12", "credit"));
        assertNum("0.02", jeSum(jv, "5.1", "credit"));
        for (String acc : List.of("1.4.0", "5.1", "5.12")) {
            assertBalanceMatchesLedger(acc);
        }

        Map<String, Object> d = jdbc.queryForMap("SELECT status, posted_run_id, posted_run_number FROM production_run_drafts WHERE id = ?",
                draft.get("id").asText());
        assertEquals("Posted", d.get("status"));
        assertEquals(runId, d.get("posted_run_id"));
        assertEquals(runNumber, d.get("posted_run_number"));

        // Detail: relasi + jurnal aktif + ringkasan GL nyata.
        JsonNode detail = ok(get(RUNS + "/" + runId));
        assertEquals(jv, detail.get("journal").get("entryNumber").asText());
        // CR GKP, DR Beras, DR Sekam, CR 5.12 (biaya konversi), CR 5.1 (sisa pembulatan HPP).
        assertEquals(5, detail.get("journal").get("items").size());
        assertTrue(detail.get("journal").get("items").get(0).get("account").has("accountNumber"));
        JsonNode gl = detail.get("glSummary");
        assertNum("1000", gl.get("inputQty"));
        assertNum("5000000", gl.get("inputValue"));
        assertNum("600", gl.get("mainQty"));
        assertNum("4930000.02", gl.get("mainValue"));
        assertNum("200", gl.get("byproductQty"));
        assertNum("200000", gl.get("byproductValue"));
        // Aritmetika angka JS (double) seperti sumber → toleransi pembulatan float.
        assertEquals(130000.02, gl.get("conversionDiff").asDouble(), 1e-6);
        assertNum("60", gl.get("yieldPct"));
        assertEquals(3, detail.get("items").size());
        JsonNode berasLine = null;
        for (JsonNode it : detail.get("items")) {
            assertTrue(it.get("item").has("averageCost"));
            assertEquals("GDG-UTAMA", it.get("warehouse").get("code").asText());
            if (s.beras().equals(it.get("itemId").asText())) {
                berasLine = it;
            }
        }
        assertEquals("Output", berasLine.get("lineType").asText());
        assertNum("8216.67", berasLine.get("unitPrice"));
        assertNum("60", berasLine.get("yieldPct"));
        assertEquals(2, detail.get("runCosts").size());
        assertTrue(detail.get("runCosts").get(0).get("costAccount").isNull());
        assertEquals(1, detail.get("runProcesses").size());
        assertTrue(detail.get("runProcesses").get(0).get("process").has("name"));
        assertTrue(detail.get("machine").isNull());
        JsonNode rm = detail.get("runMachines").get(0);
        assertNum("390", rm.get("runMin"));
        assertNum("800", rm.get("allocatedQty"));
        assertNum("68.57", rm.get("oeePct"));
        assertTrue(rm.get("machine").has("idealRatePerHour"));
        assertEquals("Breakdown", detail.get("downtimes").get(0).get("category").asText());
        assertTrue(detail.get("downtimes").get(0).get("machine").has("code"));
        assertTrue(detail.get("createdBy").has("fullName"));
        assertEquals(404, call(get(RUNS + "/00000000-0000-4000-8000-000000000000")).status());

        // Daftar & ringkasan memakai filter yang sama.
        JsonNode list = ok(get(RUNS + "?search=" + runNumber));
        assertEquals(1, list.get("total").asInt());
        assertEquals(50, list.get("limit").asInt());
        JsonNode row = list.get("data").get(0);
        assertEquals(3, row.get("items").size());
        assertTrue(row.get("items").get(0).get("item").has("code"));
        assertTrue(row.get("machine").isNull());
        assertTrue(row.get("createdBy").has("id"));
        assertEquals(1, ok(get(RUNS + "?search=" + itemName(s.gkp()).toLowerCase())).get("total").asInt());
        assertEquals(0, ok(get(RUNS + "?status=cancelled&search=" + runNumber)).get("total").asInt());
        assertEquals("Status proses produksi tidak dikenal.", call(get(RUNS + "?status=batal")).error());
        assertEquals("Format tanggal harus YYYY-MM-DD.", call(get(RUNS + "?startDate=2026-1-1")).error());
        assertEquals(1, ok(get(RUNS + "?sort=-yieldPct&startDate=" + today() + "&endDate=" + today() + "&search=" + runNumber))
                .get("total").asInt());

        JsonNode sum = ok(get(RUNS + "/summary?search=" + runNumber));
        assertEquals(1, sum.get("count").asInt());
        assertEquals(1, sum.get("activeCount").asInt());
        assertEquals(0, sum.get("cancelledCount").asInt());
        assertNum("1000", sum.get("inputQty"));
        assertNum("800", sum.get("outputQty"));
        assertNum("200", sum.get("byproductQty"));
        assertNum("60", sum.get("yieldPct"));
        assertEquals("kg", sum.get("unit").asText());
    }

    @Test
    void direct_edit_replays_stock_wac_and_posts_revision_journal() throws Exception {
        asAdmin();
        Scenario s = scenario();
        JsonNode run = submitRun(standardBody(s));
        String runId = run.get("id").asText();
        String jv = "JV-" + run.get("runNumber").asText();

        JsonNode edited = ok(put(RUNS + "/" + runId, editedBody(s)));
        assertEquals(run.get("runNumber").asText(), edited.get("runNumber").asText());
        assertEquals(3, edited.get("items").size());
        assertNum("65", edited.get("yieldPct"));
        // (5.000.000 − 150.000) / 650
        assertNum("7461.5385", edited.get("computedHpp"));
        assertTrue(edited.get("oeePct").isNull());

        assertEquals(3, cancelledMovements(runId));
        assertEquals(3, activeMovements(runId));
        assertNum("0", stock(s.gkp()));
        assertNum("650", stock(s.beras()));
        assertNum("150", stock(s.sekam()));
        assertNum("650", whStock(s.beras()));
        assertNum("5000", avg(s.gkp()));
        assertNum("7461.54", avg(s.beras()));

        assertEquals("Cancelled", jeStatus(jv));
        assertEquals("Submitted", jeStatus(jv + "-R1"));
        BigDecimal[] t = jeTotals(jv + "-R1");
        assertNum("5000000.03", t[0]);
        assertNum("5000000.03", t[1]);
        assertNum("5000000.03", t[2]);
        assertNum("0.03", jeSum(jv + "-R1", "5.1", "credit"));
        assertNum("0", jeSum(jv + "-R1", "5.12", "credit"));
        assertNum("0", jeTotals(jv)[2]); // ledger jurnal lama dibatalkan
        for (String acc : List.of("1.4.0", "5.1", "5.12")) {
            assertBalanceMatchesLedger(acc);
        }
        JsonNode detail = ok(get(RUNS + "/" + runId));
        assertEquals(jv + "-R1", detail.get("journal").get("entryNumber").asText());
        assertEquals(0, detail.get("runCosts").size());
        assertEquals(0, detail.get("runMachines").size());
        assertEquals(0, detail.get("downtimes").size());

        // Edit kedua → revisi R2, R1 dibatalkan.
        ok(put(RUNS + "/" + runId, editedBody(s)));
        assertEquals("Cancelled", jeStatus(jv + "-R1"));
        assertEquals("Submitted", jeStatus(jv + "-R2"));
        assertNum("650", stock(s.beras()));
        assertBalanceMatchesLedger("1.4.0");
    }

    @Test
    void cancel_reverses_stock_wac_and_journals_with_guards() throws Exception {
        asAdmin();
        Scenario s = scenario();
        JsonNode run = submitRun(standardBody(s));
        String runId = run.get("id").asText();
        String jv = "JV-" + run.get("runNumber").asText();

        // Tanpa doc.cancel → 403.
        asUser("prod.cancel." + uid().toLowerCase() + "@test.local", "ERP_PRODUCTION");
        assertEquals(403, call(put(RUNS + "/" + runId + "/cancel", map())).status());

        asAdmin();
        JsonNode res = ok(put(RUNS + "/" + runId + "/cancel", map()));
        assertEquals("Proses produksi dibatalkan.", res.get("message").asText());
        assertNum("1000", stock(s.gkp()));
        assertNum("1000", whStock(s.gkp()));
        assertNum("0", stock(s.beras()));
        assertNum("0", stock(s.sekam()));
        assertNum("5000", avg(s.gkp()));
        assertEquals(0, activeMovements(runId));
        assertEquals(3, cancelledMovements(runId));
        assertEquals("Cancelled", jeStatus(jv));
        assertNum("0", jeTotals(jv)[2]);
        for (String acc : List.of("1.4.0", "5.1", "5.12")) {
            assertBalanceMatchesLedger(acc);
        }
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT is_cancelled FROM production_runs WHERE id = ?", Boolean.class, runId));
        assertEquals("Proses produksi sudah dibatalkan.", call(put(RUNS + "/" + runId + "/cancel", map())).error());
        Result editCancelled = call(put(RUNS + "/" + runId, editedBody(s)));
        assertEquals(400, editCancelled.status());
        assertEquals("Proses produksi yang sudah dibatalkan tidak bisa diedit.", editCancelled.error());
        assertEquals(1, ok(get(RUNS + "/summary?search=" + run.get("runNumber").asText())).get("cancelledCount").asInt());

        // Output sudah terpakai → pembatalan ditolak kecuali bypass beralasan.
        JsonNode run2 = submitRun(standardBody(s));
        String run2Id = run2.get("id").asText();
        stockOut(s.beras(), "100");
        Result guarded = call(put(RUNS + "/" + run2Id + "/cancel", map()));
        assertEquals(400, guarded.status());
        assertEquals("Tidak dapat membatalkan: stok '" + itemName(s.beras()) + "' tidak mencukupi untuk pembalikan output "
                + "(tersedia 500, perlu 600). Output sudah dipakai produksi/penjualan berikutnya. Batalkan transaksi tersebut dulu.",
                guarded.error());
        assertEquals(3, activeMovements(run2Id));
        assertEquals("Alasan bypass stok minus wajib diisi (minimal 10 karakter).",
                call(put(RUNS + "/" + run2Id + "/cancel?allowNegativeStock=true", map("negativeStockReason", "pendek"))).error());
        ok(put(RUNS + "/" + run2Id + "/cancel?allowNegativeStock=true", map("negativeStockReason", "Koreksi produksi salah input")));
        assertNum("-100", stock(s.beras()));
        assertBalanceMatchesLedger("1.4.0");
    }

    @Test
    void draft_lifecycle_warnings_and_post_failure_records_error() throws Exception {
        asAdmin();
        String bahan = item("Bahan");
        String hasil = item("Hasil");
        stockIn(bahan, "10", "1000");
        String bahanName = itemName(bahan);
        Map<String, Object> body = map("date", today(), "inputs", List.of(input(bahan, 50)),
                "outputs", List.of(output(hasil, 40, null, false)));
        JsonNode created = createDraft(body);
        assertEquals(List.of("Stok '" + bahanName + "' saat ini 10 kg, dibutuhkan 50 kg. Dicek ulang saat posting."),
                json.convertValue(created.get("warnings"), List.class));
        String draftId = created.get("draft").get("id").asText();
        String draftNumber = created.get("draft").get("draftNumber").asText();

        JsonNode list = ok(get(DRAFTS + "?search=" + draftNumber));
        assertEquals(1, list.get("total").asInt());
        assertFalse(list.get("data").get(0).has("payload"));
        assertTrue(list.get("data").get(0).get("createdBy").has("fullName"));
        assertTrue(list.get("counts").get("Draft").asInt() >= 1);
        assertNum("50", ok(get(DRAFTS + "/" + draftId)).get("payload").get("inputs").get(0).get("quantity"));
        assertEquals("Status draft tidak dikenal.", call(get(DRAFTS + "?status=Lain")).error());
        assertEquals("Format tanggal harus YYYY-MM-DD.", call(get(DRAFTS + "?endDate=kemarin")).error());
        assertEquals(404, call(get(DRAFTS + "/00000000-0000-4000-8000-000000000000")).status());

        body.put("inputs", List.of(input(bahan, 60)));
        JsonNode updated = ok(put(DRAFTS + "/" + draftId, body));
        assertNum("60", updated.get("draft").get("payload").get("inputs").get(0).get("quantity"));
        assertTrue(updated.get("warnings").get(0).asText().contains("dibutuhkan 60 kg"));

        // Pengguna lain (bukan pembuat, tanpa approval.bypass) tidak boleh mengubah.
        asUser("prod.other." + uid().toLowerCase() + "@test.local", "ERP_PRODUCTION");
        Result foreign = call(put(DRAFTS + "/" + draftId, body));
        assertEquals(403, foreign.status());
        assertEquals("Hanya pembuat draft yang boleh mengubahnya.", foreign.error());
        assertEquals("Hanya pembuat draft yang boleh memostingnya.", call(post(DRAFTS + "/" + draftId + "/post", map())).error());

        asAdmin();
        Result failed = call(post(DRAFTS + "/" + draftId + "/post", map()));
        assertEquals(400, failed.status());
        String msg = "Stok '" + bahanName + "' tidak cukup. Tersedia: 10 kg, dibutuhkan: 60 kg. Buat penerimaan/transfer dulu.";
        assertEquals(msg, failed.error());
        Map<String, Object> row = jdbc.queryForMap("SELECT status, last_error FROM production_run_drafts WHERE id = ?", draftId);
        assertEquals("Draft", row.get("status"));
        assertEquals(msg, row.get("last_error"));
        assertNum("10", stock(bahan));

        JsonNode discarded = ok(delete(DRAFTS + "/" + draftId));
        assertEquals("Draft produksi dihapus.", discarded.get("message").asText());
        assertEquals("Discarded", jdbc.queryForObject("SELECT status FROM production_run_drafts WHERE id = ?", String.class, draftId));
        assertEquals("Draft hanya bisa diubah selama berstatus Draft.", call(put(DRAFTS + "/" + draftId, body)).error());
        assertEquals("Draft sudah diproses atau tidak lagi berstatus Draft.", call(post(DRAFTS + "/" + draftId + "/post", map())).error());
        assertEquals("Hanya draft berstatus Draft yang bisa dihapus.", call(delete(DRAFTS + "/" + draftId)).error());
        assertEquals(0, ok(get(DRAFTS + "?search=" + draftNumber)).get("total").asInt());
    }

    @Test
    void batch_pick_locks_input_cost_and_hpp_override_drives_output_value() throws Exception {
        asAdmin();
        Scenario s = scenario();
        String batchId = jdbc.queryForObject("SELECT id FROM stock_movements WHERE item_id = ? AND movement_type = 'In'",
                String.class, s.gkp());

        // Validasi batch terjadi saat posting (draft tidak memeriksa batch).
        Map<String, Object> missingBatch = editedBody(s);
        missingBatch.put("inputs", List.of(map("itemId", s.gkp(), "quantity", 1000,
                "sourceMovementId", "00000000-0000-4000-8000-000000000000")));
        String d1 = createDraft(missingBatch).get("draft").get("id").asText();
        assertEquals("Batch sumber tidak ditemukan untuk input ke-1.", call(post(DRAFTS + "/" + d1 + "/post", map())).error());

        Map<String, Object> body = map("date", today(), "warehouseId", wh(),
                "inputs", List.of(map("itemId", s.gkp(), "quantity", 1000, "sourceMovementId", batchId, "batchUnitCost", 4800)),
                "outputs", List.of(output(s.beras(), 600, null, false), output(s.sekam(), 200, 1000, true)),
                "hppOverride", 9000);
        JsonNode run = submitRun(body);
        String runId = run.get("id").asText();
        String jv = "JV-" + run.get("runNumber").asText();
        assertNum("9000", run.get("computedHpp"));
        assertTrue(run.get("hppManualOverride").asBoolean());
        assertNum("4800000", run.get("totalMaterialCost"));
        Map<String, Object> out = jdbc.queryForMap("SELECT unit_cost, total_value, source_movement_id FROM stock_movements "
                + "WHERE reference_id = ? AND movement_type = 'Out'", runId);
        assertNum("4800", (BigDecimal) out.get("unit_cost"));
        assertNum("4800000", (BigDecimal) out.get("total_value"));
        assertEquals(batchId, out.get("source_movement_id"));
        Map<String, Object> line = jdbc.queryForMap("SELECT source_movement_id, batch_unit_cost FROM production_run_items "
                + "WHERE production_run_id = ? AND line_type = 'Input'", runId);
        assertEquals(batchId, line.get("source_movement_id"));
        assertNum("4800", (BigDecimal) line.get("batch_unit_cost"));
        assertNum("9000", avg(s.beras()));
        // CR bahan 4,8 jt; DR Beras 600 × 9.000 + Sekam 200.000; tanpa biaya konversi → selisih ke 5.1.
        assertNum("4800000", jeSum(jv, "1.4.0", "credit"));
        assertNum("5600000", jeSum(jv, "1.4.0", "debit"));
        assertNum("800000", jeSum(jv, "5.1", "credit"));
        BigDecimal[] t = jeTotals(jv);
        assertNum("5600000", t[0]);
        assertNum("5600000", t[1]);
        assertEquals(batchId, ok(get(RUNS + "/" + runId)).get("items").get(0).get("sourceMovement").get("id").asText());

        // Gerakan keluar bukan batch yang sah.
        String outMovement = jdbc.queryForObject("SELECT id FROM stock_movements WHERE reference_id = ? AND movement_type = 'Out'",
                String.class, runId);
        stockIn(s.gkp(), "10", "5000");
        Map<String, Object> badBatch = map("date", today(), "inputs", List.of(map("itemId", s.gkp(), "quantity", 1,
                "sourceMovementId", outMovement)), "outputs", List.of(output(s.beras(), 1, null, false)));
        String d2 = createDraft(badBatch).get("draft").get("id").asText();
        assertEquals("Batch sumber bukan stok masuk (input ke-1).", call(post(DRAFTS + "/" + d2 + "/post", map())).error());
        Map<String, Object> wrongItem = map("date", today(), "inputs", List.of(map("itemId", s.gkp(), "quantity", 1,
                "sourceMovementId", jdbc.queryForObject("SELECT id FROM stock_movements WHERE reference_id = ? AND item_id = ?",
                        String.class, runId, s.beras()))), "outputs", List.of(output(s.beras(), 1, null, false)));
        String d3 = createDraft(wrongItem).get("draft").get("id").asText();
        assertEquals("Batch sumber tidak cocok dengan item input ke-1.", call(post(DRAFTS + "/" + d3 + "/post", map())).error());
    }

    @Test
    void validation_messages_follow_zod_schema() throws Exception {
        asAdmin();
        String a = item("Val A");
        String b = item("Val B");
        assertEquals("Invalid input: expected array, received undefined",
                call(post(RUNS, map("date", today(), "outputs", List.of(output(b, 1, null, false))))).error());
        assertEquals("Minimal satu item input.",
                call(post(RUNS, map("date", today(), "inputs", List.of(), "outputs", List.of(output(b, 1, null, false))))).error());
        assertEquals("Tanggal wajib diisi.",
                call(post(RUNS, map("date", "", "inputs", List.of(input(a, 1)), "outputs", List.of(output(b, 1, null, false))))).error());
        assertEquals("Kuantitas harus lebih dari 0.",
                call(post(RUNS, map("date", today(), "inputs", List.of(input(a, 0)), "outputs", List.of(output(b, 1, null, false))))).error());
        assertEquals("Invalid input: expected number, received NaN",
                call(post(RUNS, map("date", today(), "inputs", List.of(input(a, "abc")), "outputs", List.of(output(b, 1, null, false))))).error());
        assertEquals("NRV produk sampingan wajib diisi (minimal 0).",
                call(post(RUNS, map("date", today(), "inputs", List.of(input(a, 1)), "outputs", List.of(output(b, 1, null, true))))).error());
        assertEquals("Alasan bypass stok minus wajib diisi (minimal 10 karakter).",
                call(post(RUNS, map("date", today(), "inputs", List.of(input(a, 1)), "outputs", List.of(output(b, 1, null, false)),
                        "allowNegativeStock", true))).error());
        assertEquals("Gudang wajib dipilih.",
                call(post(RUNS, map("date", today(), "warehouseId", "gudang", "inputs", List.of(input(a, 1)),
                        "outputs", List.of(output(b, 1, null, false))))).error());
        assertEquals("Invalid input: expected date, received Date",
                call(post(RUNS, map("date", today(), "plannedStart", "besok pagi", "inputs", List.of(input(a, 1)),
                        "outputs", List.of(output(b, 1, null, false))))).error());

        // Bypass stok minus butuh capability inventory.negative_stock.
        asUser("prod.neg." + uid().toLowerCase() + "@test.local", "ERP_PRODUCTION");
        Result neg = call(post(RUNS, map("date", today(), "inputs", List.of(input(a, 1)), "outputs", List.of(output(b, 1, null, false)),
                "allowNegativeStock", true, "negativeStockReason", "Produksi mundur tanggal")));
        assertEquals(403, neg.status());
        assertEquals("Anda tidak berwenang bypass validasi stok.", neg.error());

        // Admin boleh bypass: posting memakai stok minus dan tercatat.
        asAdmin();
        JsonNode run = submitRun(map("date", today(), "inputs", List.of(input(a, 5)), "outputs", List.of(output(b, 4, 2000, false)),
                "allowNegativeStock", true, "negativeStockReason", "Produksi mundur tanggal"));
        assertNum("-5", stock(a));
        assertNum("4", stock(b));
        // Bahan tanpa WAC (0) → HPP loaded 0; output berharga eksplisit → selisih seluruhnya CR 5.1.
        assertNum("0", run.get("computedHpp"));
        String jv = "JV-" + run.get("runNumber").asText();
        assertNum("8000", jeSum(jv, "5.1", "credit"));
        assertNum("8000", jeTotals(jv)[0]);
        assertNum("8000", jeTotals(jv)[1]);
        assertNull(jdbc.queryForObject("SELECT unit_price FROM production_run_items WHERE production_run_id = ? AND line_type = 'Input'",
                BigDecimal.class, run.get("id").asText()));
    }
}
