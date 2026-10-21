package id.enterpriseapps.erp.paymentrequest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Master Jenis Pengajuan Pembayaran: GET terbuka, tulis butuh settings edit; jenis terpakai tidak bisa dihapus. */
class PaymentRequestTypeTest extends PaymentRequestTestSupport {

    private static final String TYPES = "/api/erp/payment-request-types";

    private static boolean contains(JsonNode list, String id) {
        for (JsonNode t : list) {
            if (id.equals(t.get("id").asText())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void crud_with_validation_and_permissions() throws Exception {
        String code = "T" + UUID.randomUUID().toString().substring(0, 6).toUpperCase().replace("-", "");
        String staffEmail = email("ty.staff");
        provision(staffEmail, "EMPLOYEE");

        // GET terbuka untuk semua pengguna login; seed jenis bawaan ada.
        asUser(staffEmail, "EMPLOYEE");
        JsonNode active = ok(get(TYPES));
        assertTrue(active.size() >= 7);
        JsonNode first = active.get(0);
        assertTrue(first.has("_count") && first.get("_count").has("requests"));
        assertTrue(first.has("defaultExpenseAccount"));
        // Tulis tanpa izin settings → 403 (gerbang rute).
        expectError(403, "Akses ditolak: Tidak memiliki izin.",
                post(TYPES, body("code", code, "name", "Uji", "kind", "OTHER")));

        asAdmin();
        expectError(400, "Kode hanya huruf besar, angka, _ atau -.", post(TYPES, body("code", "ab c", "name", "Uji", "kind", "OTHER")));
        expectError(400, "Too small: expected string to have >=2 characters", post(TYPES, body("code", "A", "name", "Uji", "kind", "OTHER")));
        expectError(400, "Invalid option: expected one of \"REIMBURSEMENT\"|\"VENDOR_PAYMENT\"|\"CASH_ADVANCE\"|\"TRAVEL\"|\"OTHER\"",
                post(TYPES, body("code", code, "name", "Uji", "kind", "LAIN")));
        expectError(400, "Akun biaya default harus akun detail yang aktif.",
                post(TYPES, body("code", code, "name", "Uji", "kind", "OTHER", "defaultExpenseAccountId", accountId("6"))));

        Result created = call(post(TYPES, body("code", code, "name", "  Uji Jenis  ", "kind", "REIMBURSEMENT",
                "description", "", "defaultExpenseAccountId", accountId("6.16"), "requiresAttachment", true, "sortOrder", 95)));
        assertEquals(201, created.status(), String.valueOf(created.body()));
        JsonNode type = created.data();
        String id = type.get("id").asText();
        assertEquals("Uji Jenis", type.get("name").asText());
        assertTrue(type.get("description").isNull());
        assertTrue(type.get("requiresAttachment").asBoolean());
        assertFalse(type.get("requiresSettlement").asBoolean());
        assertTrue(type.get("isActive").asBoolean());
        assertEquals("6.16", type.get("defaultExpenseAccount").get("accountNumber").asText());
        expectError(400, "Kode jenis pengajuan sudah dipakai.", post(TYPES, body("code", code, "name", "Dobel", "kind", "OTHER")));

        // Nonaktifkan → hilang dari daftar formulir, tetap ada di ?all=1.
        JsonNode updated = ok(put(TYPES + "/" + id, body("code", code, "name", "Uji Jenis 2", "kind", "OTHER",
                "isActive", false, "sortOrder", 96)));
        assertEquals("Uji Jenis 2", updated.get("name").asText());
        assertFalse(updated.get("isActive").asBoolean());
        assertTrue(updated.get("defaultExpenseAccount").isNull());
        assertFalse(contains(ok(get(TYPES)), id));
        assertTrue(contains(ok(get(TYPES + "?all=1")), id));
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.",
                put(TYPES + "/" + UUID.randomUUID(), body("code", code + "X", "name", "Tidak ada", "kind", "OTHER")));

        // Jenis nonaktif tidak bisa dipakai untuk pengajuan baru.
        asUser(staffEmail, "EMPLOYEE");
        expectError(400, "Jenis pengajuan tidak ditemukan atau tidak aktif.",
                post(BASE, draft(id, "Pakai jenis nonaktif", List.of(line("x", 1000, null)))));

        // Aktifkan lagi, pakai, lalu hapus ditolak; jenis lain yang belum dipakai bisa dihapus.
        asAdmin();
        ok(put(TYPES + "/" + id, body("code", code, "name", "Uji Jenis 2", "kind", "OTHER", "isActive", true)));
        asUser(staffEmail, "EMPLOYEE");
        createDraft(id, "Pakai jenis uji", List.of(line("x", 1000, null)));
        asAdmin();
        JsonNode withCount = null;
        for (JsonNode t : ok(get(TYPES + "?all=true"))) {
            if (id.equals(t.get("id").asText())) {
                withCount = t;
            }
        }
        assertEquals(1, withCount.get("_count").get("requests").asInt());
        expectError(400, "Jenis ini sudah dipakai 1 pengajuan. Nonaktifkan saja agar riwayat tetap utuh.", delete(TYPES + "/" + id));

        String code2 = code + "B";
        String id2 = call(post(TYPES, body("code", code2, "name", "Sekali pakai", "kind", "TRAVEL"))).data().get("id").asText();
        JsonNode deleted = ok(delete(TYPES + "/" + id2));
        assertTrue(deleted.get("ok").asBoolean());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM payment_request_types WHERE id = ?", Integer.class, id2));
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.", delete(TYPES + "/" + id2));
    }
}
