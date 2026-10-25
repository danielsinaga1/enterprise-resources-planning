package id.neravoxa.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aset tetap: register + jadwal, posting penyusutan tunggal & bulk (jurnal seimbang, akumulasi, idempoten per
 * periode), pelepasan (tanpa jurnal, keluar dari run), tautan EAM, impor, daftar & ringkasan.
 */
class FixedAssetFlowTest extends EamAssetTestSupport {

    private static final int YEAR = LocalDate.now(ZoneOffset.UTC).getYear();

    private Map<String, Object> body(String name, String category, String acquisitionDate, long cost, int life) {
        return map("name", name, "category", category, "acquisitionDate", acquisitionDate, "acquisitionCost", cost,
                "usefulLifeMonths", life, "assetAccountId", accountId("1.6.1"),
                "depreciationAccountId", accountId("6.21"), "accumulatedDepAccountId", accountId("1.7.1"));
    }

    private int journalCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM journal_entries", Integer.class);
    }

    private Map<String, Object> entry(String fixedAssetId, String periodDay) {
        return jdbc.queryForMap("SELECT id, amount, is_posted, journal_entry_id FROM depreciation_entries "
                + "WHERE fixed_asset_id = ? AND period_date = CAST(? AS timestamp)", fixedAssetId, periodDay);
    }

    /** Jurnal Submitted seimbang: Dr 6.21 / Cr 1.7.1 sebesar amount; ledger 2 baris. */
    private void assertDepreciationJournal(String journalId, BigDecimal amount, String narration) {
        Map<String, Object> je = jdbc.queryForMap("SELECT entry_number, status, narration FROM journal_entries WHERE id = ?",
                journalId);
        assertEquals("Submitted", je.get("status"));
        assertTrue(je.get("entry_number").toString().matches("DEP-\\d{6}"), je.get("entry_number").toString());
        assertEquals(narration, je.get("narration"));
        List<Map<String, Object>> lines = jdbc.queryForList("SELECT a.account_number, j.debit, j.credit FROM journal_entry_accounts j "
                + "JOIN accounts a ON a.id = j.account_id WHERE j.journal_entry_id = ? ORDER BY j.debit DESC", journalId);
        assertEquals(2, lines.size());
        assertEquals("6.21", lines.get(0).get("account_number"));
        assertEquals(0, amount.compareTo((BigDecimal) lines.get(0).get("debit")));
        assertEquals("1.7.1", lines.get(1).get("account_number"));
        assertEquals(0, amount.compareTo((BigDecimal) lines.get(1).get("credit")));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM accounting_ledger_entries WHERE reference_id = ?",
                Integer.class, journalId));
    }

    @Test
    void register_depreciation_run_disposal_and_eam_link() throws Exception {
        asAdmin();
        String category = uniq("Kendaraan ");
        String branchId = createBranch(uniq("F")).get("id").asText();
        JsonNode ga = createAsset("KENDARAAN", branchId, "Truk Aset Tetap");
        String gaId = ga.get("id").asText();
        String name = uniq("Truk FA ");

        // Validasi gaya Zod.
        Map<String, Object> bad = body("", category, YEAR + "-01-15", 12000000, 12);
        expectError(400, "Nama aset wajib diisi.", post(FA, bad));
        expectError(400, "Harga perolehan harus lebih dari 0.", post(FA, body(name, category, YEAR + "-01-15", 0, 12)));
        Map<String, Object> badAcc = body(name, category, YEAR + "-01-15", 1000, 12);
        badAcc.put("assetAccountId", "bukan-uuid");
        expectError(400, "assetAccountId harus UUID valid.", post(FA, badAcc));
        // Di luar rentang acak FiscalYearFlowTest.freeYear() (1900–1999) yang berbagi database yang sama.
        expectError(400, "Tahun buku tidak ditemukan untuk tanggal akuisisi.", post(FA, body(name, category, "1850-01-15", 1000, 12)));
        Map<String, Object> badGa = body(name, category, YEAR + "-01-15", 1000, 12);
        badGa.put("gaAssetId", "tidak-ada");
        expectError(400, "Aset EAM yang dipilih tidak ditemukan.", post(FA, badGa));

        // Register + jadwal 12 bulan (tanggal 1 tiap bulan setelah perolehan).
        Map<String, Object> req = body(name, category, YEAR + "-01-15", 12000000, 12);
        req.put("gaAssetId", " " + gaId + " ");
        Result created = call(post(FA, req));
        assertEquals(201, created.status(), String.valueOf(created.body()));
        JsonNode fa = created.data();
        String faId = fa.get("id").asText();
        String assetNumber = fa.get("assetNumber").asText();
        assertTrue(assetNumber.matches("FA-\\d{4}"));
        assertEquals(0, new BigDecimal("12000000").compareTo(fa.get("bookValue").decimalValue()));
        assertEquals("1.6.1", fa.at("/assetAccount/accountNumber").asText());
        assertEquals(3, fa.get("assetAccount").size());
        assertEquals(ga.get("code").asText(), fa.at("/gaAsset/code").asText());
        assertFalse(fa.has("user"));
        assertEquals("Active", fa.get("status").asText());

        JsonNode detail = ok(get(FA + "/" + faId));
        JsonNode entries = detail.get("depreciationEntries");
        assertEquals(12, entries.size());
        assertEquals(YEAR + "-02-01T00:00:00.000Z", entries.get(0).get("periodDate").asText());
        assertEquals((YEAR + 1) + "-01-01T00:00:00.000Z", entries.get(11).get("periodDate").asText());
        assertEquals(0, new BigDecimal("1000000").compareTo(entries.get(0).get("amount").decimalValue()));
        assertTrue(detail.at("/user").has("fullName"));

        // Satu aset EAM hanya untuk satu aset tetap; aset EAM menampilkan profil keuangannya.
        Map<String, Object> dup = body(uniq("Dup "), category, YEAR + "-01-15", 1000, 12);
        dup.put("gaAssetId", gaId);
        expectError(409, "Aset EAM ini sudah ditautkan ke aset tetap " + assetNumber + " (" + name
                + "). Satu aset EAM hanya boleh punya satu aset tetap.", post(FA, dup));
        assertEquals(assetNumber, ok(get(ASSETS + "/" + gaId)).at("/fixedAsset/assetNumber").asText());

        // Posting satu periode.
        BigDecimal exp0 = balance("6.21");
        BigDecimal acc0 = balance("1.7.1");
        String entryId = entries.get(0).get("id").asText();
        JsonNode dep = ok(post(FA + "/" + faId + "/depreciate", map("entryId", entryId)));
        assertTrue(dep.get("success").asBoolean());
        String jeId = dep.get("journalEntryId").asText();
        BigDecimal million = new BigDecimal("1000000");
        assertDepreciationJournal(jeId, million, "Depresiasi " + name + " - " + YEAR + "-02");
        assertEquals(0, exp0.add(million).compareTo(balance("6.21")));
        assertEquals(0, acc0.subtract(million).compareTo(balance("1.7.1")));
        detail = ok(get(FA + "/" + faId));
        assertEquals(0, million.compareTo(detail.get("accumulatedDepreciation").decimalValue()));
        assertEquals(0, new BigDecimal("11000000").compareTo(detail.get("bookValue").decimalValue()));
        assertTrue(detail.at("/depreciationEntries/0/isPosted").asBoolean());
        assertEquals(jeId, detail.at("/depreciationEntries/0/journalEntryId").asText());
        expectError(400, "Depresiasi sudah diposting.", post(FA + "/" + faId + "/depreciate", map("entryId", entryId)));
        expectError(404, "Jadwal depresiasi tidak ditemukan.",
                post(FA + "/" + faId + "/depreciate", map("entryId", "3f1c2b4a-1d2e-4f5a-8b9c-0d1e2f3a4b5c")));
        expectError(400, "entryId harus UUID valid.", post(FA + "/" + faId + "/depreciate", map("entryId", "x")));

        // Run bulanan: pratinjau, posting, lalu idempoten untuk periode yang sama.
        String march = YEAR + "-03";
        JsonNode dry = ok(post(FA + "/depreciate-bulk", map("month", march, "dryRun", true)));
        assertTrue(dry.get("dryRun").asBoolean());
        assertEquals(0, dry.get("posted").asInt());
        boolean listed = false;
        for (JsonNode it : dry.get("items")) {
            if (assetNumber.equals(it.get("assetNumber").asText())) {
                listed = true;
                assertEquals(0, million.compareTo(it.get("amount").decimalValue()));
            }
        }
        assertTrue(listed);
        int before = journalCount();
        JsonNode run = ok(post(FA + "/depreciate-bulk", map("month", march)));
        int posted = run.get("posted").asInt();
        assertTrue(posted >= 1);
        assertEquals(posted, run.get("jeNumbers").size());
        assertEquals(before + posted, journalCount());
        assertEquals("Berhasil post " + posted + " entry depresiasi (" + posted + " jurnal) untuk periode " + march + ".",
                run.get("message").asText());
        Map<String, Object> marchEntry = entry(faId, YEAR + "-03-01");
        assertEquals(true, marchEntry.get("is_posted"));
        assertDepreciationJournal((String) marchEntry.get("journal_entry_id"), million, "Depresiasi " + name + " - " + march);
        assertEquals(0, new BigDecimal("2000000").compareTo(ok(get(FA + "/" + faId)).get("accumulatedDepreciation").decimalValue()));
        JsonNode again = ok(post(FA + "/depreciate-bulk", map("month", march)));
        assertEquals(0, again.get("posted").asInt());
        assertEquals("Tidak ada jadwal depresiasi belum ter-post di periode " + march + ".", again.get("message").asText());
        assertEquals(before + posted, journalCount());
        expectError(400, "Format month harus 'YYYY-MM'.", post(FA + "/depreciate-bulk", map("month", YEAR + "-3")));

        // Edit metadata + lepas tautan EAM; nominal tidak berubah.
        JsonNode upd = ok(put(FA + "/" + faId, map("name", name + " X", "gaAssetId", "")));
        assertEquals(name + " X", upd.get("name").asText());
        assertTrue(upd.get("gaAsset").isNull());
        assertTrue(upd.at("/user").has("fullName"));
        assertEquals(0, new BigDecimal("12000000").compareTo(upd.get("acquisitionCost").decimalValue()));
        upd = ok(put(FA + "/" + faId, map("gaAssetId", gaId)));
        assertEquals(gaId, upd.at("/gaAsset/id").asText());
        expectError(404, "Aset tidak ditemukan.", put(FA + "/tidak-ada", map("name", "X")));

        // Pelepasan: hanya status (tanpa jurnal), lalu tidak ikut run bulanan.
        int jBefore = journalCount();
        JsonNode disp = ok(post(FA + "/" + faId + "/dispose", map("disposalDate", YEAR + "-04-10", "disposalAmount", 0)));
        assertTrue(disp.get("success").asBoolean());
        assertEquals(jBefore, journalCount());
        detail = ok(get(FA + "/" + faId));
        assertEquals("Disposed", detail.get("status").asText());
        assertEquals(YEAR + "-04-10T00:00:00.000Z", detail.get("disposalDate").asText());
        expectError(400, "Aset sudah tidak aktif.", post(FA + "/" + faId + "/dispose", map("disposalDate", YEAR + "-04-11")));
        expectError(400, "Tanggal pelepasan wajib diisi.", post(FA + "/" + faId + "/dispose", map("disposalDate", "")));
        expectError(404, "Aset tidak ditemukan.", post(FA + "/tidak-ada/dispose", map("disposalDate", YEAR + "-04-11")));
        ok(post(FA + "/depreciate-bulk", map("month", YEAR + "-04")));
        assertEquals(false, entry(faId, YEAR + "-04-01").get("is_posted"));
        assertEquals(0, new BigDecimal("2000000").compareTo(ok(get(FA + "/" + faId)).get("accumulatedDepreciation").decimalValue()));
    }

    @Test
    void december_run_import_schedule_and_lists() throws Exception {
        asAdmin();
        String category = uniq("Mesin-");

        // Run Desember: tahun buku berakhir 31 Des 00:00 tetap ditemukan.
        String decName = uniq("Mesin Des ");
        String decId = ok(post(FA, body(decName, category, YEAR + "-11-15", 1000, 2))).get("id").asText();
        JsonNode dec = ok(post(FA + "/depreciate-bulk", map("month", YEAR + "-12")));
        assertTrue(dec.get("posted").asInt() >= 1, String.valueOf(dec));
        Map<String, Object> decEntry = entry(decId, YEAR + "-12-01");
        assertEquals(true, decEntry.get("is_posted"));
        assertDepreciationJournal((String) decEntry.get("journal_entry_id"), new BigDecimal("500.00"),
                "Depresiasi " + decName + " - " + YEAR + "-12");

        // Impor: jadwal ala setMonth JavaScript (31 Jan → 1 Mar, 1 Mar, 1 Mei), sisa pembulatan di entri terakhir.
        expectError(400, "Too small: expected array to have >=1 items", post(FA + "/import", map("assets", List.of())));
        expectError(400, "Invalid input: expected array, received undefined", post(FA + "/import", map()));
        Map<String, Object> zero = body(uniq("Nol "), category, "2015-01-31", 0, 3);
        expectError(400, "Too small: expected number to be >0", post(FA + "/import", map("assets", List.of(zero))));
        String impName = uniq("Impor-");
        Map<String, Object> row1 = body(impName, category, "2015-01-31", 1000, 3);
        Map<String, Object> row2 = body(uniq("Impor2 "), category, "2015-06-01", 600, 6);
        JsonNode imp = ok(post(FA + "/import", map("assets", List.of(row1, row2))));
        assertTrue(imp.get("success").asBoolean());
        assertEquals(2, imp.get("count").asInt());
        assertEquals(9, imp.get("scheduleEntries").asInt());
        assertEquals(impName, imp.at("/created/0/name").asText());
        String impId = jdbc.queryForObject("SELECT id FROM fixed_assets WHERE name = ?", String.class, impName);
        List<Map<String, Object>> sched = jdbc.queryForList("SELECT to_char(period_date, 'YYYY-MM-DD') AS d, amount "
                + "FROM depreciation_entries WHERE fixed_asset_id = ? ORDER BY period_date, amount", impId);
        assertEquals(List.of("2015-03-01", "2015-03-01", "2015-05-01"), sched.stream().map(r -> r.get("d")).toList());
        for (Map<String, Object> r : sched) {
            assertEquals(0, new BigDecimal("333.33").compareTo((BigDecimal) r.get("amount")));
        }
        // Tanpa tahun buku 2015 → fallback tahun buku terbaru; tanpa jurnal saldo awal.
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM depreciation_entries WHERE fixed_asset_id = ? "
                + "AND is_posted = true", Integer.class, impId));

        // Register berpaginasi, kategori, ringkasan.
        JsonNode page = ok(get(FA + "?page=1&limit=10&category=" + category + "&sort=name"));
        assertEquals(3, page.get("total").asInt());
        assertEquals(10, page.get("limit").asInt());
        assertTrue(page.at("/data/0").has("gaAsset"));
        assertTrue(page.at("/data/0/user").has("fullName"));
        JsonNode search = ok(get(FA + "?page=1&search=" + impName.toLowerCase()));
        assertEquals(1, search.get("total").asInt());
        JsonNode ranged = ok(get(FA + "?page=1&category=" + category + "&startDate=2015-01-01&endDate=2015-12-31"));
        assertEquals(2, ranged.get("total").asInt());
        expectError(400, "Status aset tidak dikenal.", get(FA + "?page=1&status=Rusak"));
        expectError(400, "Format tanggal harus YYYY-MM-DD.", get(FA + "?page=1&startDate=2015-1-1"));
        JsonNode legacy = ok(get(FA + "?category=" + category));
        assertTrue(legacy.isArray());
        assertEquals(3, legacy.size());
        assertFalse(legacy.get(0).has("gaAsset"));

        boolean hasCat = false;
        for (JsonNode c : ok(get(FA + "/categories"))) {
            hasCat |= category.equals(c.asText());
        }
        assertTrue(hasCat);
        JsonNode summary = ok(get(FA + "/summary/by-category"));
        JsonNode mine = null;
        for (JsonNode s : summary) {
            if (category.equals(s.get("category").asText())) {
                mine = s;
            }
        }
        assertTrue(mine != null);
        assertEquals(3, mine.get("count").asInt());
        assertEquals(0, new BigDecimal("2600").compareTo(mine.get("total_cost").decimalValue()));
        assertEquals(0, new BigDecimal("500").compareTo(mine.get("total_accumulated").decimalValue()));
        assertEquals(0, new BigDecimal("2100").compareTo(mine.get("total_book_value").decimalValue()));
    }
}
