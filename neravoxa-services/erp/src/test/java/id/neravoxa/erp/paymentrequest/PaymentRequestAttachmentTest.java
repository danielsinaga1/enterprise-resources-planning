package id.neravoxa.erp.paymentrequest;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.storage.ErpFileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Lampiran: unggah multipart (validasi tipe/ukuran/jumlah gaya multer + magic byte), akses unduh mengikuti
 * visibilitas pengajuan, hapus hanya saat dapat diubah, wajib lampiran untuk jenis tertentu, jalur JSON base64.
 */
class PaymentRequestAttachmentTest extends PaymentRequestTestSupport {

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13, 0x49, 0x48};

    @Autowired
    ErpFileStorage storage;

    private final List<String> requestIds = new ArrayList<>();

    @AfterEach
    void removeStoredFiles() throws Exception {
        for (String id : requestIds) {
            for (String path : jdbc.queryForList("SELECT file_path FROM payment_request_attachments WHERE payment_request_id = ?",
                    String.class, id)) {
                Files.deleteIfExists(storage.resolve(path));
            }
        }
    }

    private MockMultipartHttpServletRequestBuilder upload(String url, MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder b = multipart(url);
        for (MockMultipartFile f : files) {
            b.file(f);
        }
        return b;
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("files", name, "application/pdf", PDF);
    }

    @Test
    void upload_access_control_download_and_delete() throws Exception {
        String reqEmail = email("at.req");
        String supEmail = email("at.sup");
        String finEmail = email("at.fin");
        String otherEmail = email("at.other");
        String requester = provision(reqEmail, "EMPLOYEE");
        String supervisor = provision(supEmail, "EMPLOYEE");
        provision(finEmail, "ERP_ACCOUNTANT");
        provision(otherEmail, "EMPLOYEE");
        setSupervisor(requester, supervisor);

        // Jenis REIMBURSE wajib lampiran.
        asUser(reqEmail, "EMPLOYEE");
        String id = createDraft(typeId("REIMBURSE"), "Penggantian bensin", List.of(line("Bensin", 200000, null)))
                .get("id").asText();
        requestIds.add(id);
        expectError(400, "Jenis pengajuan ini wajib melampirkan bukti (nota/kuitansi/tagihan).",
                post(BASE + "/" + id + "/submit", Map.of()));

        // Validasi gaya multer (sebelum cek akses) + magic byte.
        String url = BASE + "/" + id + "/attachments";
        expectError(400, "Tipe file tidak didukung: text/plain. Hanya JPG, PNG, WebP, dan PDF.",
                upload(url, new MockMultipartFile("files", "catatan.txt", "text/plain", "halo".getBytes())));
        expectError(400, "Maksimal 5 file per unggahan.",
                upload(url, pdf("1.pdf"), pdf("2.pdf"), pdf("3.pdf"), pdf("4.pdf"), pdf("5.pdf"), pdf("6.pdf")));
        expectError(400, "Ukuran file maksimal 5 MB.",
                upload(url, new MockMultipartFile("files", "besar.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1])));
        expectError(400, "File \"palsu.png\" tidak dikenali sebagai JPG/PNG/WebP/PDF.",
                upload(url, new MockMultipartFile("files", "palsu.png", "image/png", PDF)));
        expectError(400, "Tidak ada file yang diupload.", multipart(url));

        // Pengguna lain tidak bisa mengunggah ke pengajuan yang tidak terlihat.
        asUser(otherEmail, "EMPLOYEE");
        expectError(404, "Pengajuan tidak ditemukan.", upload(url, pdf("nota.pdf")));

        asUser(reqEmail, "EMPLOYEE");
        Result created = call(upload(url, pdf("dir/nota bensin.pdf"), new MockMultipartFile("files", "foto.png", "image/png", PNG)));
        assertEquals(201, created.status(), String.valueOf(created.body()));
        JsonNode rows = created.data();
        assertEquals(2, rows.size());
        assertEquals("nota bensin.pdf", rows.get(0).get("fileName").asText());
        assertEquals("application/pdf", rows.get(0).get("mimeType").asText());
        assertEquals("REQUEST", rows.get(0).get("scope").asText());
        assertEquals(requester, rows.get(0).get("uploadedBy").asText());
        String attId = rows.get(0).get("id").asText();
        String pngId = rows.get(1).get("id").asText();
        assertTrue(Files.exists(storage.resolve(rows.get(0).get("filePath").asText())));

        // Unduh: pengaju (inline PDF), Keuangan (izin modul) — orang lain 404.
        MvcResult dl = raw(get(BASE + "/attachments/" + attId));
        assertEquals(200, dl.getResponse().getStatus());
        assertEquals("application/pdf", dl.getResponse().getContentType());
        assertEquals("inline", dl.getResponse().getHeader("Content-Disposition"));
        assertEquals("nosniff", dl.getResponse().getHeader("X-Content-Type-Options"));
        assertArrayEquals(PDF, dl.getResponse().getContentAsByteArray());
        asUser(finEmail, "ERP_ACCOUNTANT");
        assertEquals(200, raw(get(BASE + "/attachments/" + attId)).getResponse().getStatus());
        asUser(otherEmail, "EMPLOYEE");
        expectError(404, "Pengajuan tidak ditemukan.", get(BASE + "/attachments/" + attId));
        expectError(404, "File tidak ditemukan.", get(BASE + "/attachments/tidak-ada"));
        asUser(supEmail, "EMPLOYEE");
        expectError(404, "Pengajuan tidak ditemukan.", get(BASE + "/attachments/" + attId));

        // Hapus selagi draf; berkas di disk ikut terhapus setelah COMMIT.
        asUser(reqEmail, "EMPLOYEE");
        String pngPath = jdbc.queryForObject("SELECT file_path FROM payment_request_attachments WHERE id = ?", String.class, pngId);
        assertTrue(ok(delete(BASE + "/" + id + "/attachments/" + pngId)).get("ok").asBoolean());
        assertFalse(Files.exists(storage.resolve(pngPath)));
        expectError(404, "File tidak ditemukan.", delete(BASE + "/" + id + "/attachments/" + pngId));

        // Dengan lampiran → bisa diajukan; atasan kini boleh melihat lampiran.
        assertEquals("SUBMITTED", ok(post(BASE + "/" + id + "/submit", Map.of())).get("status").asText());
        expectError(400, "Pengajuan hanya bisa diubah saat masih Draf atau Perlu Revisi.",
                delete(BASE + "/" + id + "/attachments/" + attId));
        expectError(400, "Pengajuan hanya bisa diubah saat masih Draf atau Perlu Revisi.", upload(url, pdf("lagi.pdf")));
        asUser(supEmail, "EMPLOYEE");
        assertEquals(200, raw(get(BASE + "/attachments/" + attId)).getResponse().getStatus());
        JsonNode detail = ok(get(BASE + "/" + id));
        assertEquals(1, detail.get("attachments").size());
        assertEquals(attId, detail.get("attachments").get(0).get("id").asText());
    }

    @Test
    void settlement_scope_and_json_base64_path() throws Exception {
        String reqEmail = email("at.req2");
        String finEmail = email("at.fin2");
        provision(reqEmail, "EMPLOYEE");
        provision(finEmail, "ERP_ACCOUNTANT");

        asUser(reqEmail, "EMPLOYEE");
        String id = createDraft(typeId("CASH_ADV"), "Uang muka lapangan", List.of(line("Operasional", 100000, null)))
                .get("id").asText();
        requestIds.add(id);

        // Jalur JSON base64 pada draf.
        String b64 = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(PDF);
        Result json = call(post(BASE + "/" + id + "/attachments/json", body("fileName", "nota.pdf", "contentBase64", b64)));
        assertEquals(201, json.status(), String.valueOf(json.body()));
        assertEquals("nota.pdf", json.data().get(0).get("fileName").asText());
        expectError(400, "fileName wajib bila contentBase64 diisi.",
                post(BASE + "/" + id + "/attachments/json", body("contentBase64", b64)));
        expectError(400, "mimeType \"image/png\" tidak cocok dengan isi berkas (terdeteksi application/pdf).",
                post(BASE + "/" + id + "/attachments/json", body("fileName", "a.pdf", "contentBase64", b64, "mimeType", "image/png")));
        expectError(400, "contentBase64 bukan base64 yang valid.",
                post(BASE + "/" + id + "/attachments/json", body("fileName", "a.pdf", "contentBase64", "!!!!!!!!!!")));

        // Bayar uang muka → lampiran pertanggungjawaban (scope=settlement) terbuka, lampiran pengajuan tertutup.
        ok(post(BASE + "/" + id + "/submit", Map.of()));
        asUser(finEmail, "ERP_ACCOUNTANT");
        ok(post(BASE + "/" + id + "/approve", Map.of()));
        ok(post(BASE + "/" + id + "/pay", body("cashAccountId", accountId("1.1.1"), "paymentDate", today())));

        asUser(reqEmail, "EMPLOYEE");
        expectError(400, "Pengajuan hanya bisa diubah saat masih Draf atau Perlu Revisi.",
                upload(BASE + "/" + id + "/attachments", pdf("struk.pdf")));
        Result settlement = call(upload(BASE + "/" + id + "/attachments?scope=settlement", pdf("struk.pdf")));
        assertEquals(201, settlement.status(), String.valueOf(settlement.body()));
        assertEquals("SETTLEMENT", settlement.data().get(0).get("scope").asText());
        String settlementAtt = settlement.data().get(0).get("id").asText();
        assertTrue(ok(delete(BASE + "/" + id + "/attachments/" + settlementAtt)).get("ok").asBoolean());

        // Keuangan bukan pengaju → tidak boleh mengisi pertanggungjawaban.
        asUser(finEmail, "ERP_ACCOUNTANT");
        expectError(400, "Hanya pengaju yang dapat mengisi pertanggungjawaban.",
                upload(BASE + "/" + id + "/attachments?scope=settlement", pdf("x.pdf")));
    }
}
