package id.enterpriseapps.erp.cashbook;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Alur Draft Buku Kas: buat → klasifikasi → ubah baris → posting (jurnal per baris, DR/CR deterministik). */
class CashBookDraftFlowTest extends CashBookTestSupport {

    private static final String BASE = "/api/erp/cash-book-drafts";

    private JsonNode createDraft(Map<String, Object> body) throws Exception {
        Result r = call(post(BASE, body));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        return r.data();
    }

    @Test
    void create_classifies_rows_and_post_writes_one_balanced_journal_per_row() throws Exception {
        asAdmin();
        String u = word();
        BigDecimal cash0 = balance("1.1.1");
        BigDecimal exp0 = balance("6.16");
        BigDecimal rev0 = balance("4.2");
        BigDecimal adv0 = balance("1.5.6");
        BigDecimal oth0 = balance("8.7");

        JsonNode d = createDraft(map("title", "  Buku   kas " + u, "entryDate", today(), "cashAccountId", accountId("1.1.1"),
                "openingBalance", 1000000, "closingBalance", "1.060.000",
                "rows", List.of(
                        map("description", "Beli kertas " + u, "keluar", "Rp 150.000", "accountCode", "6.16 Beban Perlengkapan Kantor"),
                        map("description", "Terima pelunasan " + u, "masuk", 250000, "accountCode", "4.2"),
                        map("description", "Kasbon budi " + u, "amount", "50000", "direction", "keluar", "txnType", "uang_muka"),
                        map("description", "Kembali kasbon budi " + u, "masuk", "20.000", "txnType", "terima_kasbon"),
                        map("description", "Lain   lain " + u, "keluar", 10000))));

        assertEquals("Draft", d.get("status").asText());
        assertTrue(d.get("draftNumber").asText().startsWith("DKB-"));
        assertEquals("Buku kas " + u, d.get("title").asText());
        assertEquals(today(), d.get("entryDate").asText());
        assertEquals("manual", d.get("source").asText());
        assertEquals("ui", d.get("createdVia").asText());
        assertEquals("1.1.1", d.get("cashAccount").get("accountNumber").asText());
        assertNotNull(d.get("createdBy").get("id").asText());
        assertEquals("/cash-book-drafts/" + d.get("id").asText(), d.get("reviewPath").asText());
        assertTrue(d.get("message").asText().startsWith("Draft dibuat. Belum ada jurnal"));
        assertEquals(5, d.get("rows").size());

        JsonNode r1 = rowByIdx(d, 1);
        assertEquals("pengeluaran", r1.get("txnType").asText());
        assertEquals("keluar", r1.get("direction").asText());
        assertEquals("6.16", r1.get("accountCode").asText());
        assertEquals("6.16 Beban Perlengkapan Kantor", r1.get("accountLabel").asText());
        assertFalse(r1.get("needsReview").asBoolean());
        assertEquals(1.0, r1.get("confidence").asDouble());
        assertMoney("150000", r1.get("amount"));
        JsonNode r2 = rowByIdx(d, 2);
        assertEquals("pemasukan", r2.get("txnType").asText());
        assertEquals("4.2", r2.get("accountCode").asText());
        JsonNode r3 = rowByIdx(d, 3);
        assertEquals("uang_muka", r3.get("txnType").asText());
        assertEquals("1.5.6", r3.get("accountCode").asText());
        assertTrue(r3.get("needsReview").asBoolean());
        JsonNode r4 = rowByIdx(d, 4);
        assertEquals("terima_kasbon", r4.get("txnType").asText());
        assertEquals("masuk", r4.get("direction").asText());
        assertEquals("1.5.6", r4.get("accountCode").asText());
        JsonNode r5 = rowByIdx(d, 5);
        assertEquals("Lain lain " + u, r5.get("description").asText());
        assertEquals("8.7", r5.get("accountCode").asText());
        assertTrue(r5.get("needsReview").asBoolean());
        assertTrue(r5.get("reason").asText().contains("belum terklasifikasi; akun usulan default beban lain-lain"));

        assertMoney("270000", d.get("totals").get("masuk"));
        assertMoney("210000", d.get("totals").get("keluar"));
        assertEquals(5, d.get("totals").get("activeCount").asInt());
        assertEquals(3, d.get("needsReviewCount").asInt());
        assertEquals(0, d.get("duplicateCount").asInt());
        assertTrue(d.get("balance").get("checked").asBoolean());
        assertTrue(d.get("balance").get("ok").asBoolean());
        assertMoney("1060000", d.get("balance").get("expectedClosing"));

        String id = d.get("id").asText();
        String number = d.get("draftNumber").asText();
        JsonNode res = ok(post(BASE + "/" + id + "/post", Map.of()));
        assertEquals("Posted", res.get("status").asText());
        assertEquals(5, res.get("postedCount").asInt());
        assertEquals(0, res.get("failed").size());
        assertEquals(0, res.get("learned").asInt());
        assertMoney("480000", res.get("totalAmount"));
        Map<Integer, String> journals = new HashMap<>();
        for (JsonNode j : res.get("journals")) {
            assertTrue(j.get("entryNumber").asText().startsWith("JV-"));
            journals.put(j.get("rowIdx").asInt(), j.get("journalEntryId").asText());
        }
        assertJournal(journals.get(1), "6.16", "1.1.1", "150000");
        assertJournal(journals.get(2), "1.1.1", "4.2", "250000");
        assertJournal(journals.get(3), "1.5.6", "1.1.1", "50000");
        assertJournal(journals.get(4), "1.1.1", "1.5.6", "20000");
        assertJournal(journals.get(5), "8.7", "1.1.1", "10000");
        String narration = jdbc.queryForObject("SELECT narration FROM journal_entries WHERE id = ?", String.class, journals.get(1));
        assertEquals("Buku Kas " + number + " #1: Beli kertas " + u, narration);

        assertMoney("60000", balance("1.1.1").subtract(cash0));
        assertMoney("150000", balance("6.16").subtract(exp0));
        assertMoney("250000", balance("4.2").subtract(rev0));
        assertMoney("30000", balance("1.5.6").subtract(adv0));
        assertMoney("10000", balance("8.7").subtract(oth0));

        JsonNode after = ok(get(BASE + "/" + id));
        assertEquals("Posted", after.get("status").asText());
        assertFalse(after.get("postedAt").isNull());
        assertEquals(0, after.get("needsReviewCount").asInt());
        assertTrue(rowByIdx(after, 1).get("journalNumber").asText().startsWith("JV-"));

        Result again = call(post(BASE + "/" + id + "/post", Map.of()));
        assertEquals(400, again.status());
        assertEquals("Draft sudah diposting atau tidak lagi bisa diposting.", again.error());
        Result edit = call(put(BASE + "/" + id, map("title", "x")));
        assertEquals(400, edit.status());
        assertEquals("Draft hanya bisa diubah selama berstatus Draft atau Sebagian Diposting.", edit.error());
        Result discard = call(post(BASE + "/" + id + "/discard", Map.of()));
        assertEquals(400, discard.status());
        assertEquals("Hanya draft berstatus Draft yang bisa dibuang.", discard.error());
    }

    @Test
    void edit_rows_blocked_and_partial_post_then_learns_pattern() throws Exception {
        asAdmin();
        String u = word();
        JsonNode d = createDraft(map("entryDate", today(), "cashAccountId", accountId("1.1.1"),
                "rows", List.of(
                        map("description", "Bayar parkir " + u, "keluar", 5000),
                        map("description", "Setoran tunai " + u, "masuk", 7000, "accountCode", "1.1.1"),
                        map("description", "Fotokopi " + u, "keluar", 3000, "accountCode", "6.16"))));
        String id = d.get("id").asText();
        assertEquals("Buku kas " + today(), d.get("title").asText());
        JsonNode r2 = rowByIdx(d, 2);
        assertEquals("7.4", r2.get("accountCode").asText());
        assertTrue(r2.get("reason").asText().contains("akun lawan sama dengan akun kas; diabaikan"));
        assertTrue(r2.get("reason").asText().contains("default pendapatan lain-lain"));
        String row1 = rowByIdx(d, 1).get("id").asText();
        String row2 = r2.get("id").asText();
        String row3 = rowByIdx(d, 3).get("id").asText();

        // Satu patch salah → tidak ada yang tersimpan (all-or-nothing) dan semua error dilaporkan per baris.
        Result bad = call(put(BASE + "/" + id + "/rows", map("rows", List.of(
                map("id", row1, "accountId", accountId("6.3")),
                map("id", row2, "txnType", "pengeluaran")))));
        assertEquals(400, bad.status());
        assertEquals("Ada 1 kesalahan pada baris: Baris 2: Jenis \"pengeluaran\" tidak cocok dengan arah kas masuk.", bad.error());
        assertEquals(List.of("Baris 2: Jenis \"pengeluaran\" tidak cocok dengan arah kas masuk."), texts(bad.data().get("rowErrors")));
        assertEquals("8.7", rowByIdx(ok(get(BASE + "/" + id)), 1).get("accountCode").asText());

        Result zod = call(put(BASE + "/" + id + "/rows", map("rows", List.of(map("id", row1, "direction", "naik")))));
        assertEquals(400, zod.status());
        assertEquals("Invalid option: expected one of \"masuk\"|\"keluar\"", zod.error());
        Result empty = call(put(BASE + "/" + id + "/rows", map("rows", List.of())));
        assertEquals("Minimal satu baris.", empty.error());

        Map<String, Object> clearAccount = new HashMap<>();
        clearAccount.put("id", row3);
        clearAccount.put("accountId", null);
        JsonNode upd = ok(put(BASE + "/" + id + "/rows", map("rows", List.of(
                map("id", row1, "accountId", accountId("6.3")),
                map("id", row2, "txnType", "terima_kasbon"),
                clearAccount))));
        assertEquals(3, upd.get("updated").asInt());
        JsonNode dr = upd.get("draft");
        JsonNode e1 = rowByIdx(dr, 1);
        assertEquals("6.3", e1.get("accountCode").asText());
        assertTrue(e1.get("userEdited").asBoolean());
        assertFalse(e1.get("needsReview").asBoolean());
        assertEquals(1.0, e1.get("confidence").asDouble());
        JsonNode e2 = rowByIdx(dr, 2);
        assertEquals("terima_kasbon", e2.get("txnType").asText());
        assertEquals("1.5.6", e2.get("accountCode").asText());
        JsonNode e3 = rowByIdx(dr, 3);
        assertTrue(e3.get("accountId").isNull());
        assertTrue(e3.get("needsReview").asBoolean());

        // Tambah baris: idx berlanjut, duplikat dalam satu kiriman ditandai.
        Result app = call(post(BASE + "/" + id + "/rows", map("rows", List.of(
                map("description", "Materai " + u, "keluar", 10000, "accountCode", "6.14"),
                map("description", "Materai " + u, "keluar", "10.000", "accountCode", "6.14")))));
        assertEquals(201, app.status());
        assertEquals(2, app.data().get("added").asInt());
        JsonNode r5 = rowByIdx(app.data().get("draft"), 5);
        assertTrue(r5.get("isDuplicate").asBoolean());
        assertTrue(r5.get("needsReview").asBoolean());
        assertTrue(r5.get("reason").asText().contains("kemungkinan duplikat (sama dengan baris 4)"));
        assertEquals(1, app.data().get("draft").get("duplicateCount").asInt());

        // Posting all-or-nothing gagal: tidak ada jurnal, error baris tercatat.
        Result blocked = call(post(BASE + "/" + id + "/post", Map.of()));
        assertEquals(400, blocked.status());
        assertEquals("Ada 1 kesalahan pada baris: Baris 3: Akun lawan belum dipilih.", blocked.error());
        assertEquals(List.of("Baris 3: Akun lawan belum dipilih."), texts(blocked.data().get("rowErrors")));
        JsonNode afterBlock = ok(get(BASE + "/" + id));
        assertEquals("Draft", afterBlock.get("status").asText());
        assertEquals("Posting gagal: Baris 3: Akun lawan belum dipilih.", afterBlock.get("lastError").asText());
        assertEquals("Akun lawan belum dipilih.", rowByIdx(afterBlock, 3).get("error").asText());
        for (JsonNode r : afterBlock.get("rows")) {
            assertTrue(r.get("journalEntryId").isNull());
        }

        Result partialBad = call(post(BASE + "/" + id + "/post", map("partial", "yes")));
        assertEquals("Invalid input: expected boolean, received string", partialBad.error());

        JsonNode partial = ok(post(BASE + "/" + id + "/post", map("partial", true)));
        assertEquals("PartiallyPosted", partial.get("status").asText());
        assertEquals(4, partial.get("postedCount").asInt());
        assertEquals(1, partial.get("failed").size());
        assertEquals(3, partial.get("failed").get(0).get("idx").asInt());
        assertEquals(1, partial.get("learned").asInt());
        assertMoney("32000", partial.get("totalAmount"));
        Map<Integer, String> journals = new HashMap<>();
        partial.get("journals").forEach(j -> journals.put(j.get("rowIdx").asInt(), j.get("journalEntryId").asText()));
        assertJournal(journals.get(1), "6.3", "1.1.1", "5000");
        assertJournal(journals.get(2), "1.1.1", "1.5.6", "7000");

        String pattern = "bayar parkir " + u;
        Map<String, Object> learned = jdbc.queryForMap("SELECT a.account_number, m.hits, m.txn_type FROM cash_book_learned_maps m "
                + "JOIN accounts a ON a.id = m.account_id WHERE m.pattern = ?", pattern);
        assertEquals("6.3", learned.get("account_number"));
        assertEquals(1, ((Number) learned.get("hits")).intValue());
        assertEquals("pengeluaran", learned.get("txn_type"));

        JsonNode pp = ok(get(BASE + "/" + id));
        assertEquals("1 baris belum diposting: Baris 3: Akun lawan belum dipilih.", pp.get("lastError").asText());
        Result lockedRow = call(put(BASE + "/" + id + "/rows", map("rows", List.of(map("id", row1, "description", "ubah")))));
        assertEquals(List.of("Baris 1: sudah diposting dan tidak bisa diubah."), texts(lockedRow.data().get("rowErrors")));
        Result cashChange = call(put(BASE + "/" + id, map("cashAccountId", accountId("1.1.2"))));
        assertEquals("Akun kas tidak bisa diganti setelah sebagian baris diposting.", cashChange.error());
        assertEquals(400, call(post(BASE + "/" + id + "/discard", Map.of())).status());

        ok(put(BASE + "/" + id + "/rows", map("rows", List.of(map("id", row3, "skip", true)))));
        JsonNode done = ok(post(BASE + "/" + id + "/post", Map.of()));
        assertEquals("Posted", done.get("status").asText());
        assertEquals(0, done.get("postedCount").asInt());

        // Pola terpelajari dipakai pada draft baru (1x koreksi → keyakinan 0,65, tetap perlu ditinjau).
        JsonNode next = createDraft(map("entryDate", today(), "cashAccountId", accountId("1.1.1"),
                "rows", List.of(map("description", "Bayar  parkir " + u, "keluar", 2500))));
        JsonNode lr = rowByIdx(next, 1);
        assertEquals("6.3", lr.get("accountCode").asText());
        assertEquals(0.65, lr.get("confidence").asDouble());
        assertTrue(lr.get("needsReview").asBoolean());
        assertTrue(lr.get("reason").asText().contains("akun dari pola terpelajari (1x dikoreksi manusia)"));

        JsonNode maps = ok(get(BASE + "/learned-maps"));
        boolean found = false;
        for (JsonNode m : maps.get("data")) {
            if (pattern.equals(m.get("pattern").asText())) {
                found = true;
                assertEquals("6.3", m.get("accountCode").asText());
            }
        }
        assertTrue(found);
    }

    @Test
    void validation_row_limits_and_review_threshold() throws Exception {
        asAdmin();
        String u = word();
        String cash = accountId("1.1.1");
        assertEquals("Invalid input: expected string, received undefined", call(post(BASE, Map.of())).error());
        assertEquals("Tanggal halaman buku kas wajib diisi.",
                call(post(BASE, map("entryDate", "", "cashAccountId", cash, "rows", List.of()))).error());
        assertEquals("Akun kas wajib dipilih.",
                call(post(BASE, map("entryDate", today(), "cashAccountId", "", "rows", List.of()))).error());
        assertEquals("Baris wajib berupa daftar.",
                call(post(BASE, map("entryDate", today(), "cashAccountId", cash, "rows", "x"))).error());
        assertEquals("Invalid input",
                call(post(BASE, map("entryDate", today(), "cashAccountId", cash, "openingBalance", true, "rows", List.of()))).error());
        assertEquals("Too big: expected string to have <=200 characters",
                call(post(BASE, map("title", "x".repeat(201), "entryDate", today(), "cashAccountId", cash, "rows", List.of()))).error());
        assertEquals("Minimal satu baris diperlukan.",
                call(post(BASE, map("entryDate", today(), "cashAccountId", cash, "rows", List.of()))).error());
        List<Map<String, Object>> one = List.of(map("description", "Satu " + u, "keluar", 1000));
        assertEquals("Tanggal halaman buku kas tidak valid (format YYYY-MM-DD).",
                call(post(BASE, map("entryDate", "2026-02-30", "cashAccountId", cash, "rows", one))).error());
        assertEquals("Akun sumber dana harus akun kas/bank (1.1.x).",
                call(post(BASE, map("entryDate", today(), "cashAccountId", accountId("6.1"), "rows", one))).error());
        assertEquals("Akun kas/bank tidak valid (harus akun detail yang aktif).",
                call(post(BASE, map("entryDate", today(), "cashAccountId", accountId("1.1"), "rows", one))).error());
        assertEquals("Saldo awal bukan angka yang valid.",
                call(post(BASE, map("entryDate", today(), "cashAccountId", cash, "openingBalance", "abc", "rows", one))).error());

        Result rows = call(post(BASE, map("entryDate", today(), "cashAccountId", cash, "rows", List.of(
                map("description", "  ", "keluar", 1),
                map("description", "x", "masuk", 1, "keluar", 2),
                map("description", "y", "amount", 5, "direction", "naik"),
                map("description", "z", "keluar", "abc"),
                map("description", "w", "date", "2026-13-01", "keluar", 1),
                map("description", "v", "keluar", -5)))));
        assertEquals(400, rows.status());
        List<String> expected = List.of("Baris 1: keterangan wajib diisi.",
                "Baris 2: isi salah satu saja, masuk atau keluar (bukan keduanya).",
                "Baris 3: arah harus \"masuk\" atau \"keluar\".",
                "Baris 4: nominal masuk/keluar bukan angka yang valid.",
                "Baris 5: tanggal \"2026-13-01\" tidak valid (format YYYY-MM-DD).",
                "Baris 6: nominal tidak boleh negatif.");
        assertEquals(expected, texts(rows.data().get("rowErrors")));
        assertEquals("Ada 6 kesalahan pada baris: " + String.join(" | ", expected.subList(0, 5)) + " | …", rows.error());

        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 1; i <= 301; i++) {
            many.add(map("description", "Barang ke " + i + " " + u, "keluar", 100 + i, "accountCode", "6.16"));
        }
        assertEquals("Maksimal 300 baris per draft (saat ini 0, menambah 301). Pecah menjadi beberapa draft.",
                call(post(BASE, map("entryDate", today(), "cashAccountId", cash, "rows", many))).error());
        JsonNode full = createDraft(map("entryDate", today(), "cashAccountId", cash, "rows", many.subList(0, 300)));
        assertEquals(300, full.get("totals").get("rowCount").asInt());
        Result over = call(post(BASE + "/" + full.get("id").asText() + "/rows", map("rows", one)));
        assertEquals("Maksimal 300 baris per draft (saat ini 300, menambah 1). Pecah menjadi beberapa draft.", over.error());

        // Ambang tinjau 0,7 + aturan "lainnya", kontradiksi arah, dan kode akun tak dikenal.
        JsonNode t = createDraft(map("entryDate", today(), "cashAccountId", cash, "rows", List.of(
                map("description", "Beban satu " + u, "keluar", 1000, "accountCode", "6.16", "confidence", 0.69),
                map("description", "Beban dua " + u, "keluar", 1000, "accountCode", "6.16", "confidence", "0.7"),
                map("description", "Beban tiga " + u, "keluar", 1000, "accountCode", "6.16", "txnType", "lainnya", "confidence", 0.95),
                map("description", "Beban empat " + u, "keluar", 1000, "accountCode", "4.1", "txnType", "pemasukan", "confidence", 0.9),
                map("description", "Beban lima " + u, "keluar", 1000, "accountCode", "9.99.99"))));
        assertTrue(rowByIdx(t, 1).get("needsReview").asBoolean());
        assertEquals("6.16", rowByIdx(t, 1).get("accountCode").asText());
        assertEquals(0.69, rowByIdx(t, 1).get("confidence").asDouble());
        assertFalse(rowByIdx(t, 2).get("needsReview").asBoolean());
        JsonNode lain = rowByIdx(t, 3);
        assertEquals("lainnya", lain.get("txnType").asText());
        assertTrue(lain.get("needsReview").asBoolean());
        assertTrue(lain.get("reason").asText().contains("jenis \"lainnya\": wajib ditinjau"));
        JsonNode contra = rowByIdx(t, 4);
        assertEquals("pengeluaran", contra.get("txnType").asText());
        assertEquals("8.7", contra.get("accountCode").asText());
        assertTrue(contra.get("needsReview").asBoolean());
        assertTrue(contra.get("reason").asText()
                .contains("jenis \"pemasukan\" tidak cocok dengan arah kas keluar; dikoreksi menjadi \"pengeluaran\""));
        JsonNode unknown = rowByIdx(t, 5);
        assertEquals("8.7", unknown.get("accountCode").asText());
        assertTrue(unknown.get("reason").asText().contains("akun \"9.99.99\" tidak ditemukan"));
    }

    @Test
    void list_accounts_header_changes_discard_and_guards() throws Exception {
        asAdmin();
        String u = word();
        JsonNode d = createDraft(map("title", "Daftar " + u, "entryDate", today(), "cashAccountId", accountId("1.1.1"),
                "rows", List.of(
                        map("description", "Masuk " + u, "masuk", 1000, "accountCode", "4.1"),
                        map("description", "Keluar " + u, "keluar", 300, "accountCode", "6.16"),
                        map("description", "Lewati " + u, "keluar", 50, "accountCode", "6.16"))));
        String id = d.get("id").asText();
        ok(put(BASE + "/" + id + "/rows", map("rows", List.of(map("id", rowByIdx(d, 3).get("id").asText(), "skip", true)))));

        JsonNode list = ok(get(BASE + "?search=" + u.toUpperCase()));
        assertEquals(1, list.get("total").asInt());
        assertEquals(1, list.get("page").asInt());
        assertEquals(25, list.get("limit").asInt());
        JsonNode item = list.get("data").get(0);
        assertEquals(id, item.get("id").asText());
        assertEquals(2, item.get("rowCount").asInt());
        assertMoney("1000", item.get("totalMasuk"));
        assertMoney("300", item.get("totalKeluar"));
        assertEquals(0, item.get("needsReviewCount").asInt());
        assertEquals("1.1.1", item.get("cashAccount").get("accountNumber").asText());
        assertEquals(today(), item.get("entryDate").asText());
        assertTrue(item.get("createdBy").has("fullName"));
        assertTrue(list.get("counts").get("Draft").asInt() >= 1);
        assertFalse(list.get("counts").has("Discarded"));

        assertEquals(1, ok(get(BASE + "?search=" + u + "&startDate=" + today() + "&endDate=" + today())).get("total").asInt());
        assertEquals(0, ok(get(BASE + "?search=" + u + "&endDate=2000-01-01")).get("total").asInt());
        assertEquals("Status draft tidak dikenal.", call(get(BASE + "?status=Foo")).error());
        assertEquals("Format tanggal harus YYYY-MM-DD.", call(get(BASE + "?startDate=2026-1-1")).error());

        Map<String, Object> header = new HashMap<>();
        header.put("title", "  Judul   baru " + u);
        header.put("closingBalance", "1.000,50");
        header.put("openingBalance", null);
        header.put("sourceNote", "catatan");
        JsonNode h = ok(put(BASE + "/" + id, header));
        assertEquals("Judul baru " + u, h.get("title").asText());
        assertEquals(1000.5, h.get("closingBalance").asDouble());
        assertTrue(h.get("openingBalance").isNull());
        assertFalse(h.get("balance").get("checked").asBoolean());
        assertEquals("catatan", h.get("sourceNote").asText());
        assertEquals("Invalid input: expected string, received number", call(put(BASE + "/" + id, map("title", 5))).error());

        // Akun kas baru tidak boleh sama dengan akun lawan baris mana pun.
        ok(put(BASE + "/" + id + "/rows", map("rows", List.of(
                map("id", rowByIdx(d, 1).get("id").asText(), "accountId", accountId("1.1.2"))))));
        assertEquals("Ada baris yang akun lawannya sama dengan akun kas yang baru. Ubah akun baris tersebut dulu.",
                call(put(BASE + "/" + id, map("cashAccountId", accountId("1.1.2")))).error());
        assertEquals("Akun sumber dana harus akun kas/bank (1.1.x).",
                call(put(BASE + "/" + id, map("cashAccountId", accountId("6.1")))).error());
        JsonNode moved = ok(put(BASE + "/" + id, map("cashAccountId", accountId("1.1.3"))));
        assertEquals("1.1.3", moved.get("cashAccount").get("accountNumber").asText());
        Result sameAsCash = call(put(BASE + "/" + id + "/rows", map("rows", List.of(
                map("id", rowByIdx(d, 2).get("id").asText(), "accountId", accountId("1.1.3"))))));
        assertEquals(List.of("Baris 2: akun lawan tidak boleh sama dengan akun kas."), texts(sameAsCash.data().get("rowErrors")));
        Result group = call(put(BASE + "/" + id + "/rows", map("rows", List.of(
                map("id", rowByIdx(d, 2).get("id").asText(), "accountId", accountId("6"))))));
        assertEquals(List.of("Baris 2: akun 6 Beban Operasional adalah akun grup (pilih akun detail)."),
                texts(group.data().get("rowErrors")));

        JsonNode discarded = ok(post(BASE + "/" + id + "/discard", Map.of()));
        assertEquals("Draft buku kas dibuang.", discarded.get("message").asText());
        assertEquals(0, ok(get(BASE + "?search=" + u)).get("total").asInt());
        assertEquals(1, ok(get(BASE + "?status=Discarded&search=" + u)).get("total").asInt());
        assertEquals("Hanya draft berstatus Draft yang bisa dibuang.", call(post(BASE + "/" + id + "/discard", Map.of())).error());

        Result missing = call(get(BASE + "/tidak-ada"));
        assertEquals(404, missing.status());
        assertEquals("Draft buku kas tidak ditemukan.", missing.error());
        assertEquals("Draft buku kas tidak ditemukan.", call(put(BASE + "/tidak-ada", map("title", "x"))).error());
        assertEquals("Draft buku kas tidak ditemukan.", call(post(BASE + "/tidak-ada/post", Map.of())).error());

        JsonNode acc = ok(get(BASE + "/accounts"));
        List<String> codes = new ArrayList<>();
        Map<String, JsonNode> byCode = new HashMap<>();
        for (JsonNode a : acc.get("accounts")) {
            codes.add(a.get("code").asText());
            byCode.put(a.get("code").asText(), a);
        }
        assertFalse(codes.contains("1.1"));
        assertTrue(codes.indexOf("6.2") < codes.indexOf("6.10"));
        assertTrue(byCode.get("1.1.1").get("isCash").asBoolean());
        assertFalse(byCode.get("6.1").get("isCash").asBoolean());
        assertEquals("EMPLOYEE_ADVANCE", byCode.get("1.5.6").get("role").asText());
        assertEquals("OTHER_EXPENSE", byCode.get("8.7").get("role").asText());
        assertEquals("OTHER_INCOME", byCode.get("7.4").get("role").asText());
        assertTrue(acc.get("cashAccounts").size() >= 5);
        assertEquals(accountId("1.5.6"), acc.get("rules").get("advanceAccountId").asText());
        assertEquals(accountId("8.7"), acc.get("rules").get("defaultExpenseAccountId").asText());
        assertEquals(300, acc.get("limits").get("maxRows").asInt());
        assertEquals(0.7, acc.get("limits").get("reviewThreshold").asDouble());
        assertEquals(5, acc.get("txnTypes").size());
        assertEquals("uang_muka", acc.get("txnTypes").get(2).get("txnType").asText());
        assertTrue(acc.get("learnedMaps").isArray());

        asUser("tanpa.izin@test.local", "SOME_OTHER_ROLE");
        assertEquals(403, call(get(BASE)).status());
    }
}
