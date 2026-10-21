package id.enterpriseapps.erp.cashbank;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Alur rekonsiliasi bank: sesi, item statement, pencocokan ledger, selesai, hapus, unggah mutasi. */
class ReconciliationFlowTest extends CashBankTestSupport {

    @Test
    void full_matching_flow() throws Exception {
        String customer = createParty(uniq("Pelanggan Rekon"), "Customer");
        asAdmin();
        JsonNode pay = ok(post("/api/erp/payments", body("date", today(), "partyId", customer, "amount", 410000,
                "paymentType", "Receive", "accountId", accountId("1.1.4"))));
        String jv = "JV-" + pay.get("paymentNumber").asText();
        String ledgerId = jdbc.queryForObject("SELECT le.id FROM accounting_ledger_entries le JOIN journal_entries je "
                + "ON je.id = le.reference_id WHERE je.entry_number = ? AND le.account_id = ?", String.class, jv, accountId("1.1.4"));
        String otherLedgerId = jdbc.queryForObject("SELECT le.id FROM accounting_ledger_entries le JOIN journal_entries je "
                + "ON je.id = le.reference_id WHERE je.entry_number = ? AND le.account_id = ?", String.class, jv, accountId("1.2.1"));

        var notCash = call(post("/api/erp/reconciliation", body("accountId", accountId("1.2.1"),
                "statementDate", today(), "statementBalance", 1000)));
        assertEquals(400, notCash.status());
        assertEquals("Akun yang dipilih bukan akun kas/bank.", notCash.error());
        var noBalance = call(post("/api/erp/reconciliation", body("accountId", accountId("1.1.4"),
                "statementDate", today(), "statementBalance", "x")));
        assertEquals("Saldo statement harus berupa angka.", noBalance.error());

        String notes = uniq("RekonOktober");
        var created = call(post("/api/erp/reconciliation", body("accountId", accountId("1.1.4"),
                "statementDate", today(), "statementBalance", "5000000", "notes", notes)));
        assertEquals(201, created.status());
        JsonNode recon = created.data();
        String id = recon.get("id").asText();
        assertEquals("Draft", recon.get("status").asText());
        assertEquals("1.1.4", recon.get("account").get("accountNumber").asText());
        assertTrue(recon.get("account").has("balance"));
        assertAmount(balance("1.1.4").toPlainString(), recon.get("bookBalance"));

        var empty = call(post("/api/erp/reconciliation/" + id + "/items", body("items", List.of())));
        assertEquals("Minimal satu item statement.", empty.error());
        var nan = call(post("/api/erp/reconciliation/" + id + "/items",
                body("items", List.of(body("statementAmount", "abc")))));
        assertEquals("Jumlah harus berupa angka.", nan.error());

        var added = call(post("/api/erp/reconciliation/" + id + "/items", body("items", List.of(
                body("statementAmount", 410000, "statementDesc", "TRF MASUK", "statementDate", today()),
                body("statementAmount", "-6500", "statementDesc", "", "statementDate", null)))));
        assertEquals(201, added.status());
        assertEquals(2, added.data().size());
        String itemId = added.data().get(0).get("id").asText();
        assertTrue(added.data().get(1).get("statementDesc").isNull());

        JsonNode detail = ok(get("/api/erp/reconciliation/" + id));
        assertEquals(2, detail.get("items").size());
        JsonNode entry = find(detail.get("unmatchedLedgerEntries"), ledgerId);
        assertEquals("Pembayaran: " + pay.get("paymentNumber").asText(), entry.get("description").asText());
        assertEquals(jv, entry.get("referenceNumber").asText());

        var wrongAccount = call(patch("/api/erp/reconciliation/" + id + "/match",
                body("itemId", itemId, "ledgerEntryId", otherLedgerId)));
        assertEquals("Ledger entry tidak ditemukan atau bukan milik akun ini.", wrongAccount.error());

        JsonNode matched = ok(patch("/api/erp/reconciliation/" + id + "/match", body("itemId", itemId, "ledgerEntryId", ledgerId)));
        assertTrue(matched.get("isMatched").asBoolean());
        assertEquals(ledgerId, matched.get("ledgerEntryId").asText());
        var twice = call(patch("/api/erp/reconciliation/" + id + "/match", body("itemId", itemId, "ledgerEntryId", ledgerId)));
        assertEquals("Item sudah dicocokkan.", twice.error());
        detail = ok(get("/api/erp/reconciliation/" + id));
        assertEquals(null, find(detail.get("unmatchedLedgerEntries"), ledgerId));

        JsonNode list = ok(get("/api/erp/reconciliation?search=" + notes.toLowerCase()));
        assertEquals(1, list.get("total").asInt());
        JsonNode row = list.get("data").get(0);
        assertEquals(2, row.get("_count").get("items").asInt());
        assertEquals(1, row.get("matchedCount").asInt());
        assertEquals(1, ok(get("/api/erp/reconciliation?status=Draft&accountId=" + accountId("1.1.4") + "&search=" + notes))
                .get("total").asInt());
        assertEquals("Status rekonsiliasi tidak dikenal.", call(get("/api/erp/reconciliation?status=Open")).error());

        JsonNode unmatched = ok(patch("/api/erp/reconciliation/" + id + "/unmatch", body("itemId", itemId)));
        assertFalse(unmatched.get("isMatched").asBoolean());
        assertTrue(unmatched.get("ledgerEntryId").isNull());

        JsonNode done = ok(post("/api/erp/reconciliation/" + id + "/complete", Map.of()));
        assertEquals("Completed", done.get("status").asText());
        assertEquals(2, done.get("items").size());
        assertFalse(done.get("account").has("balance"));
        assertEquals("Rekonsiliasi sudah selesai.", call(post("/api/erp/reconciliation/" + id + "/items",
                body("items", List.of(body("statementAmount", 1))))).error());
        assertEquals("Hanya rekonsiliasi berstatus Draft yang dapat dihapus.",
                call(delete("/api/erp/reconciliation/" + id)).error());
    }

    @Test
    void complete_requires_items_and_draft_can_be_deleted() throws Exception {
        asAdmin();
        JsonNode recon = ok(post("/api/erp/reconciliation", body("accountId", accountId("1.1.5"),
                "statementDate", today(), "statementBalance", 0)));
        String id = recon.get("id").asText();
        assertEquals("Tidak ada item statement. Tambahkan item terlebih dahulu.",
                call(post("/api/erp/reconciliation/" + id + "/complete", Map.of())).error());
        JsonNode deleted = ok(delete("/api/erp/reconciliation/" + id));
        assertEquals("Rekonsiliasi dihapus.", deleted.get("message").asText());
        var gone = call(get("/api/erp/reconciliation/" + id));
        assertEquals(404, gone.status());
        assertEquals("Rekonsiliasi tidak ditemukan.", gone.error());
    }

    @Test
    void statement_csv_upload_creates_items() throws Exception {
        asAdmin();
        JsonNode recon = ok(post("/api/erp/reconciliation", body("accountId", accountId("1.1.5"),
                "statementDate", today(), "statementBalance", 0)));
        String id = recon.get("id").asText();
        String csv = "Mutasi Rekening;;;\n"
                + "Tanggal;Keterangan;Debit;Kredit\n"
                + "19/10/2026;Setoran tunai;;1.500.000,00\n"
                + "20/10/2026;Biaya admin;6.500;\n"
                + ";Saldo akhir;;\n";
        MockMultipartFile file = new MockMultipartFile("file", "mutasi.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        var res = call(MockMvcRequestBuilders.multipart("/api/erp/reconciliation/" + id + "/items/import").file(file));
        assertEquals(201, res.status(), String.valueOf(res.body()));
        JsonNode items = res.data();
        assertEquals(2, items.size());
        assertAmount("1500000", items.get(0).get("statementAmount"));
        assertEquals("Setoran tunai", items.get(0).get("statementDesc").asText());
        assertEquals("2026-10-19T00:00:00.000Z", items.get(0).get("statementDate").asText());
        assertAmount("-6500", items.get(1).get("statementAmount"));

        MockMultipartFile bad = new MockMultipartFile("file", "mutasi.csv", "text/csv",
                "Tanggal,Keterangan,Jumlah\n31/02/2026,X,100\n".getBytes(StandardCharsets.UTF_8));
        var invalid = call(MockMvcRequestBuilders.multipart("/api/erp/reconciliation/" + id + "/items/import").file(bad));
        assertEquals(400, invalid.status());
        assertEquals("Ada 1 baris mutasi yang tidak valid.", invalid.error());
    }

    private static JsonNode find(JsonNode arr, String id) {
        for (JsonNode n : arr) {
            if (id.equals(n.get("id").asText())) {
                return n;
            }
        }
        return null;
    }
}
