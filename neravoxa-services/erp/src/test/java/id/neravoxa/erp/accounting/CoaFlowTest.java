package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Alur /api/erp/coa: pohon, buat (rootType diturunkan), guard, saldo awal (jurnal OB), hapus. */
class CoaFlowTest extends AccountingTestSupport {

    @Test
    void create_derives_root_type_and_rejects_duplicates() throws Exception {
        asAdmin();
        String number = "1.1." + rnd();
        Map<String, Object> body = Map.of("accountNumber", number, "name", "Kas Uji " + uniq(),
                "accountType", "ASSET", "rootType", "LIABILITY", "parentId", accountId("1.1"));
        Result r = call(post("/api/erp/coa", body));
        assertEquals(201, r.status());
        assertEquals("ASSET", r.data().get("rootType").asText());
        assertFalse(r.data().get("isGroup").asBoolean());
        assertFalse(r.data().has("children"));

        Result dup = call(post("/api/erp/coa", body));
        assertEquals(409, dup.status());
        assertEquals("Nomor akun sudah digunakan.", dup.error());

        Result invalid = call(post("/api/erp/coa", Map.of("accountNumber", "9.9", "name", "x",
                "accountType", "ASET", "rootType", "ASSET")));
        assertEquals(400, invalid.status());
        assertEquals("Tipe akun harus salah satu dari: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE", invalid.error());

        // Pohon: akun baru ada di bawah 1 → 1.1, node hanya berisi skalar + children.
        JsonNode tree = ok(get("/api/erp/coa"));
        JsonNode root1 = find(tree, "1");
        assertNotNull(root1);
        assertFalse(root1.has("parent"));
        JsonNode cashGroup = find(root1.get("children"), "1.1");
        assertNotNull(find(cashGroup.get("children"), number));

        JsonNode flat = ok(get("/api/erp/coa/flat"));
        JsonNode mine = find(flat, number);
        assertEquals(8, mine.size());
        assertTrue(mine.has("balance"));
    }

    @Test
    void update_guards_system_accounts_and_groups() throws Exception {
        JsonNode acc = newAccount("6", "EXPENSE");
        String id = acc.get("id").asText();
        JsonNode upd = ok(put("/api/erp/coa/" + id, Map.of("name", "Beban Diubah")));
        assertEquals("Beban Diubah", upd.get("name").asText());

        // Ganti nomor ke prefix 1 → rootType & accountType ikut diperbaiki.
        String newNumber = "1.1." + rnd();
        JsonNode moved = ok(put("/api/erp/coa/" + id, Map.of("accountNumber", newNumber)));
        assertEquals("ASSET", moved.get("rootType").asText());
        assertEquals("ASSET", moved.get("accountType").asText());

        Result taken = call(put("/api/erp/coa/" + id, Map.of("accountNumber", "1.1.1")));
        assertEquals(409, taken.status());
        assertEquals("Nomor akun sudah digunakan oleh akun lain.", taken.error());

        Result sys = call(put("/api/erp/coa/" + accountId("3.1"), Map.of("accountNumber", "3.1.99")));
        assertEquals(400, sys.status());
        assertTrue(sys.error().startsWith("Nomor akun 3.1 tidak dapat diubah karena dipakai sebagai akun sistem (OPENING_EQUITY)"));

        Result group = call(put("/api/erp/coa/" + accountId("1.1"), Map.of("isGroup", false)));
        assertEquals("Tidak dapat mengubah akun grup yang masih memiliki sub-akun.", group.error());

        Result missing = call(put("/api/erp/coa/nope-" + uniq(), Map.of("name", "x")));
        assertEquals(404, missing.status());
        assertEquals("Akun tidak ditemukan.", missing.error());
    }

    @Test
    void opening_balance_posts_balanced_ob_journal_against_opening_equity() throws Exception {
        JsonNode acc = newAccount("1.1", "ASSET");
        String id = acc.get("id").asText();
        String equity = accountId("3.1");
        BigDecimal equityBefore = balance(equity);

        JsonNode after = ok(patch("/api/erp/coa/" + id + "/balance", Map.of("balance", "1000000")));
        assertEquals(0, new BigDecimal("1000000").compareTo(after.get("balance").decimalValue()));
        assertEquals(0, equityBefore.add(new BigDecimal("1000000")).compareTo(balance(equity)));

        String jeId = jdbc.queryForObject("SELECT j.id FROM journal_entries j JOIN journal_entry_accounts i "
                + "ON i.journal_entry_id = j.id WHERE i.account_id = ? AND j.entry_number LIKE 'OB-%'", String.class, id);
        assertEquals(0, ledgerImbalance(jeId).signum());
        assertEquals("Saldo Awal: " + acc.get("accountNumber").asText() + " - " + acc.get("name").asText(),
                jdbc.queryForObject("SELECT narration FROM journal_entries WHERE id = ?", String.class, jeId));

        // Turunkan ke 400.000 → kredit akun 600.000, debit ekuitas.
        JsonNode lowered = ok(patch("/api/erp/coa/" + id + "/balance", Map.of("balance", 400000)));
        assertEquals(0, new BigDecimal("400000").compareTo(lowered.get("balance").decimalValue()));
        assertEquals(0, equityBefore.add(new BigDecimal("400000")).compareTo(balance(equity)));

        // Selisih < 0,01 → tidak ada jurnal baru.
        ok(patch("/api/erp/coa/" + id + "/balance", Map.of("balance", 400000.004)));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM journal_entry_accounts WHERE account_id = ?",
                Integer.class, id));

        assertEquals("Saldo harus berupa angka.",
                call(patch("/api/erp/coa/" + id + "/balance", Map.of("balance", "abc"))).error());
        assertEquals("Tidak bisa set saldo untuk akun grup.",
                call(patch("/api/erp/coa/" + accountId("1.1") + "/balance", Map.of("balance", 5))).error());
        assertEquals("Akun tidak ditemukan.",
                call(patch("/api/erp/coa/nope-" + uniq() + "/balance", Map.of("balance", 5))).error());

        // Akun bertransaksi tidak bisa dihapus.
        assertEquals("Tidak dapat menghapus akun yang sudah memiliki transaksi.",
                call(delete("/api/erp/coa/" + id)).error());
    }

    @Test
    void delete_guards_and_success() throws Exception {
        asAdmin();
        assertEquals("Tidak dapat menghapus akun yang masih memiliki sub-akun. Hapus sub-akun terlebih dahulu.",
                call(delete("/api/erp/coa/" + accountId("1.1"))).error());
        assertTrue(call(delete("/api/erp/coa/" + accountId("3.1"))).error()
                .startsWith("Tidak dapat menghapus akun ini karena dipakai sebagai Akun Sistem (OPENING_EQUITY)."));

        JsonNode withBalance = newAccount("1.1", "ASSET");
        jdbc.update("UPDATE accounts SET balance = 1500.5 WHERE id = ?", withBalance.get("id").asText());
        assertEquals("Tidak dapat menghapus akun dengan saldo tidak nol (1.500,5).",
                call(delete("/api/erp/coa/" + withBalance.get("id").asText())).error());

        JsonNode fresh = newAccount("6", "EXPENSE");
        String id = fresh.get("id").asText();
        asUser("akuntan." + uniq() + "@test.local", "ERP_ACCOUNTANT");
        assertEquals(403, call(delete("/api/erp/coa/" + id)).status());

        asAdmin();
        assertEquals("Akun dihapus.", ok(delete("/api/erp/coa/" + id)).get("message").asText());
        Result again = call(delete("/api/erp/coa/" + id));
        assertEquals(404, again.status());
        assertEquals("Akun tidak ditemukan.", again.error());
    }

    private static JsonNode find(JsonNode arr, String number) {
        for (JsonNode n : arr) {
            if (number.equals(n.get("accountNumber").asText())) {
                return n;
            }
        }
        return null;
    }
}
