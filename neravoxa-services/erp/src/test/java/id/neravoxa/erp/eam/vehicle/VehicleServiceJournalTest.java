package id.neravoxa.erp.eam.vehicle;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Log servis → jurnal GL lewat kernel (DR 6.7 / CR kas, seimbang), guard log berjurnal, batal jurnal
 * (status Cancelled, saldo kembali), posting menyusul; pengajuan servis → approve membuat Work Order.
 */
class VehicleServiceJournalTest extends VehicleTestSupport {

    private void assertBalanced(String journalId, BigDecimal amount) {
        assertEquals(0, journalSum(journalId, "debit").compareTo(amount));
        assertEquals(0, journalSum(journalId, "credit").compareTo(amount));
    }

    @Test
    void service_log_posts_and_cancels_balanced_journal() throws Exception {
        provision(email("veh.svc"), "SUPER_ADMIN");
        String t = tag();
        String asset = newVehicle("KDR-" + t);
        String url = BASE + "/assets/" + asset + "/services";
        String day = today().toString();

        expectError(400, "Invalid input: expected string, received undefined", post(url, body("jenisService", "OLI", "deskripsi", "x")));
        expectError(400, "Tanggal wajib diisi.", post(url, body("tanggal", "", "jenisService", "OLI", "deskripsi", "x")));
        expectError(400, "Too small: expected string to have >=1 characters",
                post(url, body("tanggal", day, "jenisService", "OLI", "deskripsi", "")));
        expectError(400, "Invalid UUID", post(url, body("tanggal", day, "jenisService", "OLI", "deskripsi", "x",
                "accountId", "bukan-uuid")));
        expectError(400, "Invalid input: expected boolean, received string",
                post(url, body("tanggal", day, "jenisService", "OLI", "deskripsi", "x", "postJournal", "ya")));
        expectError(400, "Biaya harus > 0 untuk diposting ke jurnal.",
                post(url, body("tanggal", day, "jenisService", "OLI", "deskripsi", "Ganti oli", "postJournal", true)));

        BigDecimal expenseBefore = balance("6.7");
        BigDecimal amount = new BigDecimal("750000");
        JsonNode log = ok(post(url, body("tanggal", day, "odometer", 5000, "jenisService", "SERVIS_BERKALA",
                "deskripsi", "Servis 5.000 km", "bengkel", "Bengkel Maju", "biaya", 750000, "postJournal", true)));
        String logId = log.get("id").asText();
        assertEquals("EAM", log.get("source").asText());
        assertFalse(log.has("workOrder"));
        JsonNode je = log.get("journalEntry");
        assertFalse(je.isNull());
        assertEquals("Submitted", je.get("status").asText());
        assertEquals("6.7", log.get("account").get("accountNumber").asText());
        assertFalse(log.get("creditAccount").isNull());
        String journalId = je.get("id").asText();
        assertBalanced(journalId, amount);
        assertEquals(0, balance("6.7").subtract(expenseBefore).compareTo(amount));
        assertEquals(5000, ok(get(BASE + "/assets/" + asset)).get("odometerTerakhir").asInt());

        JsonNode list = ok(get(url));
        assertEquals(1, list.size());
        assertTrue(list.get(0).get("workOrder").isNull());
        assertEquals(je.get("entryNumber").asText(), list.get(0).get("journalEntry").get("entryNumber").asText());

        // Log berjurnal aktif tidak boleh diubah nilainya / dihapus.
        expectError(400, "Service log ini sudah diposting ke jurnal. Batalkan jurnalnya dulu sebelum mengubah biaya/tanggal/deskripsi.",
                patch(url + "/" + logId, body("biaya", 800000)));
        JsonNode bengkel = ok(patch(url + "/" + logId, body("bengkel", "Bengkel Jaya")));
        assertEquals("Bengkel Jaya", bengkel.get("bengkel").asText());
        assertFalse(bengkel.has("journalEntry"));
        expectError(400, "Service log ini sudah diposting ke jurnal. Batalkan jurnalnya dulu sebelum menghapus log.",
                delete(url + "/" + logId));
        expectError(400, "Service log ini sudah memiliki jurnal. Batalkan jurnal lama sebelum posting ulang.",
                post(url + "/" + logId + "/journal", Map.of()));

        // Batal jurnal → Cancelled, tautan dilepas, saldo beban kembali.
        JsonNode cancelled = ok(delete(url + "/" + logId + "/journal"));
        assertEquals("Jurnal " + je.get("entryNumber").asText() + " dibatalkan.", cancelled.get("message").asText());
        assertEquals("Cancelled", jdbc.queryForObject("SELECT status FROM journal_entries WHERE id = ?", String.class, journalId));
        assertTrue(ok(get(url)).get(0).get("journalEntry").isNull());
        assertEquals(0, balance("6.7").compareTo(expenseBefore));
        expectError(400, "Service log ini belum memiliki jurnal.", delete(url + "/" + logId + "/journal"));

        // Setelah batal, nilai boleh diubah lalu diposting menyusul.
        JsonNode edited = ok(patch(url + "/" + logId, body("biaya", 800000, "odometer", null)));
        assertEquals(0, edited.get("biaya").decimalValue().compareTo(new BigDecimal("800000")));
        assertTrue(edited.get("odometer").isNull());
        JsonNode posted = ok(post(url + "/" + logId + "/journal", Map.of()));
        String entry2 = posted.get("journalEntry").get("entryNumber").asText();
        assertEquals("Jurnal " + entry2 + " dibuat.", posted.get("message").asText());
        assertEquals(2, posted.get("journalEntry").get("items").size());
        assertBalanced(posted.get("journalEntry").get("id").asText(), new BigDecimal("800000"));
        assertEquals(0, balance("6.7").subtract(expenseBefore).compareTo(new BigDecimal("800000")));

        // Log tanpa jurnal bisa dihapus.
        JsonNode plain = ok(post(url, body("tanggal", day, "jenisService", "AC", "deskripsi", "Isi freon", "biaya", 150000)));
        assertTrue(plain.get("journalEntry").isNull());
        expectStatus(204, delete(url + "/" + plain.get("id").asText()));
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.", delete(url + "/" + plain.get("id").asText()));
        expectError(400, "Service log tidak ditemukan.", post(url + "/tidak-ada/journal", Map.of()));
    }

    @Test
    void service_request_approval_creates_work_order() throws Exception {
        String owner = email("veh.req");
        String ownerId = provision(owner, "SUPER_ADMIN");
        String other = email("veh.req2");
        provision(other, "SUPER_ADMIN");
        asUser(owner, "SUPER_ADMIN");
        String t = tag();
        String asset = newVehicle("KDR-" + t);
        ok(put(BASE + "/assets/" + asset, body("noPlat", "F " + t)));
        String url = BASE + "/assets/" + asset + "/service-requests";

        expectError(400, "Invalid option: expected one of \"OLI\"|\"TUNE_UP\"|\"SERVIS_BERKALA\"|\"PERBAIKAN\"|\"GANTI_BAN\"|\"ELEKTRIKAL\"|\"AC\"|\"LAINNYA\"",
                post(url, body("deskripsi", "x")));
        JsonNode sr = ok(post(url, body("jenisService", "OLI", "odometer", 12000, "deskripsi", "Oli sudah hitam")));
        assertEquals("SUBMITTED", sr.get("status").asText());
        String srId = sr.get("id").asText();
        JsonNode sr2 = ok(post(url, body("jenisService", "PERBAIKAN", "deskripsi", "Rem bunyi", "urgensi", "URGENT")));

        JsonNode list = ok(get(BASE + "/service-requests?assetId=" + asset));
        assertEquals(2, list.size());
        // urgensi desc dalam status yang sama: URGENT dulu.
        assertEquals(sr2.get("id").asText(), list.get(0).get("id").asText());
        assertEquals("MEDIUM", list.get(1).get("urgensi").asText());
        assertEquals("KDR-" + t, list.get(0).get("asset").get("code").asText());
        expectError(500, "Gagal mengambil pengajuan.", get(BASE + "/service-requests?status=XYZ"));

        JsonNode approved = ok(patch(BASE + "/service-requests/" + srId + "/approve", Map.of()));
        assertEquals("APPROVED", approved.get("status").asText());
        String woCode = approved.get("workOrderCode").asText();
        assertTrue(woCode.matches("WO-\\d{4}-\\d{4}"), woCode);
        Map<String, Object> wo = jdbc.queryForMap("SELECT type, status, priority, title, description, reported_by_id "
                + "FROM ga_work_orders WHERE id = ?", approved.get("workOrderId").asText());
        assertEquals("PREVENTIVE", wo.get("type"));
        assertEquals("OPEN", wo.get("status"));
        assertEquals("MEDIUM", wo.get("priority"));
        assertEquals("Ganti Oli - F " + t, wo.get("title"));
        assertEquals("Oli sudah hitam\n\n[Dibuat otomatis dari pengajuan servis | Odometer: 12000 km]", wo.get("description"));
        assertEquals(ownerId, wo.get("reported_by_id"));
        expectError(400, "Tidak bisa approve dari status APPROVED.", patch(BASE + "/service-requests/" + srId + "/approve", Map.of()));
        expectError(404, "Pengajuan tidak ditemukan.", patch(BASE + "/service-requests/tidak-ada/approve", Map.of()));

        JsonNode detail = ok(get(BASE + "/service-requests/" + srId));
        assertEquals(woCode, detail.get("workOrder").get("code").asText());
        assertEquals("F " + t, detail.get("asset").get("vehicleData").get("noPlat").asText());
        assertTrue(detail.get("serviceLog").isNull());
        assertEquals(woCode, ok(get(BASE + "/service-requests?assetId=" + asset + "&status=APPROVED"))
                .get(0).get("workOrder").get("code").asText());

        // Pengajuan kedua: kode WO berurutan, CORRECTIVE; pembatalan hanya oleh pengaju.
        String sr2Id = sr2.get("id").asText();
        asUser(other, "SUPER_ADMIN");
        expectError(403, "Hanya pengaju yang bisa membatalkan.", patch(BASE + "/service-requests/" + sr2Id + "/cancel", Map.of()));
        assertEquals(0, ok(get(BASE + "/service-requests?mine=true&assetId=" + asset)).size());
        expectError(400, "Invalid input: expected string, received undefined",
                patch(BASE + "/service-requests/" + sr2Id + "/reject", Map.of()));
        expectError(400, "Alasan wajib diisi.", patch(BASE + "/service-requests/" + sr2Id + "/reject", body("reason", "")));
        JsonNode rejected = ok(patch(BASE + "/service-requests/" + sr2Id + "/reject", body("reason", "Belum perlu")));
        assertEquals("REJECTED", rejected.get("status").asText());
        asUser(owner, "SUPER_ADMIN");
        expectError(400, "Tidak bisa cancel dari status REJECTED.", patch(BASE + "/service-requests/" + sr2Id + "/cancel", Map.of()));
        assertEquals("Belum perlu", jdbc.queryForObject("SELECT rejected_reason FROM ga_vehicle_service_requests WHERE id = ?",
                String.class, sr2Id));

        JsonNode sr3 = ok(post(url, body("jenisService", "ELEKTRIKAL", "deskripsi", "Lampu mati")));
        JsonNode approved3 = ok(patch(BASE + "/service-requests/" + sr3.get("id").asText() + "/approve", Map.of()));
        int seq1 = Integer.parseInt(woCode.substring(woCode.lastIndexOf('-') + 1));
        String code3 = approved3.get("workOrderCode").asText();
        assertTrue(Integer.parseInt(code3.substring(code3.lastIndexOf('-') + 1)) > seq1);
        assertEquals("CORRECTIVE", jdbc.queryForObject("SELECT type FROM ga_work_orders WHERE id = ?", String.class,
                approved3.get("workOrderId").asText()));
        assertEquals("Perbaikan Elektrikal - F " + t, jdbc.queryForObject("SELECT title FROM ga_work_orders WHERE id = ?",
                String.class, approved3.get("workOrderId").asText()));
    }
}
