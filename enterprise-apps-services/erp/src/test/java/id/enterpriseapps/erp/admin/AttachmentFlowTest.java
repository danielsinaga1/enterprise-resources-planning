package id.enterpriseapps.erp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.storage.ErpFileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unggah/daftar/unduh/hapus lampiran privat dengan cek akses dokumen induk, validasi tipe & magic bytes. */
class AttachmentFlowTest extends AdminTestSupport {

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0x0d, 0x49, 0x48, 0x44, 0x52};

    @Autowired
    ErpFileStorage storage;

    private static MockMultipartHttpServletRequestBuilder upload(String type, String id, MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder b = MockMvcRequestBuilders.multipart("/api/erp/attachments/upload");
        for (MockMultipartFile f : files) {
            b.file(f);
        }
        if (type != null) {
            b.param("referenceType", type);
        }
        if (id != null) {
            b.param("referenceId", id);
        }
        return b;
    }

    private static MockMultipartFile file(String name, String mime, byte[] bytes) {
        return new MockMultipartFile("files", name, mime, bytes);
    }

    @Test
    void approval_request_attachments_follow_requester_access() throws Exception {
        loginAdmin();
        String u = uid();
        String email = "att." + u + "@test.local";
        String requesterId = login(email, "ERP_ACCOUNTANT");
        String requestId = queue(ErpAdminTestApprovalHandler.TYPE, map("label", "ATT"), "1000", requesterId);

        asUser(email, "ERP_ACCOUNTANT");
        Result created = call(upload("approval_request", requestId, file("bukti transfer.pdf", "application/pdf", PDF),
                file("foto.png", "image/png", PNG)));
        assertEquals(201, created.status(), String.valueOf(created.body()));
        JsonNode rows = created.data();
        assertEquals(2, rows.size());
        JsonNode pdf = rows.get(0);
        assertEquals("bukti transfer.pdf", pdf.get("fileName").asText());
        assertEquals("application/pdf", pdf.get("mimeType").asText());
        assertEquals(PDF.length, pdf.get("fileSize").asInt());
        assertEquals(requesterId, pdf.get("uploadedBy").asText());
        assertTrue(pdf.get("storedName").asText().endsWith(".pdf"));
        assertFalse(pdf.has("user"));
        String pdfId = pdf.get("id").asText();
        String pngId = rows.get(1).get("id").asText();
        String pngPath = rows.get(1).get("filePath").asText();
        assertTrue(Files.isRegularFile(storage.resolve(pngPath)));

        JsonNode listed = ok(get("/api/erp/attachments/approval_request/" + requestId));
        assertEquals(2, listed.size());
        assertEquals("att." + u, listed.get(0).get("user").get("fullName").asText());

        MvcResult dl = raw(get("/api/erp/attachments/file/" + pdfId));
        assertEquals(200, dl.getResponse().getStatus());
        assertEquals("application/pdf", dl.getResponse().getContentType());
        assertEquals("attachment; filename=\"bukti transfer.pdf\"; filename*=UTF-8''bukti%20transfer.pdf",
                dl.getResponse().getHeader("Content-Disposition"));
        assertArrayEquals(PDF, dl.getResponse().getContentAsByteArray());
        MvcResult img = raw(get("/api/erp/attachments/file/" + pngId));
        assertEquals("inline", img.getResponse().getHeader("Content-Disposition"));
        assertArrayEquals(PNG, img.getResponse().getContentAsByteArray());

        JsonNode counts = ok(post("/api/erp/attachments/counts", map("referenceType", "approval_request",
                "referenceIds", List.of(requestId, UUID.randomUUID().toString()))));
        assertEquals(1, counts.size());
        assertEquals(2, counts.get(requestId).asInt());
        assertEquals(0, ok(post("/api/erp/attachments/counts", map("referenceType", "approval_request", "referenceIds", List.of()))).size());
        assertEquals("referenceType tidak dikenal.", call(post("/api/erp/attachments/counts",
                map("referenceType", "nope", "referenceIds", List.of("x")))).error());

        // Validasi berkas: tipe (Content-Type), magic bytes, jumlah, referensi.
        assertEquals("Tipe file tidak didukung: text/plain. Hanya JPG, PNG, WebP, dan PDF.",
                call(upload("approval_request", requestId, file("catatan.txt", "text/plain", "halo".getBytes(StandardCharsets.UTF_8)))).error());
        assertEquals("File \"palsu.pdf\" Content-Type (application/pdf) tidak cocok dengan isi file (image/png).",
                call(upload("approval_request", requestId, file("palsu.pdf", "application/pdf", PNG))).error());
        assertEquals("File \"x.png\" tidak dikenali sebagai JPG/PNG/WebP/PDF (magic-byte mismatch).",
                call(upload("approval_request", requestId, file("x.png", "image/png", new byte[]{1, 2, 3, 4, 5}))).error());
        assertEquals("Maksimal 5 file per transaksi. Saat ini sudah ada 2 file.", call(upload("approval_request", requestId,
                file("a.pdf", "application/pdf", PDF), file("b.pdf", "application/pdf", PDF), file("c.pdf", "application/pdf", PDF),
                file("d.pdf", "application/pdf", PDF))).error());
        assertEquals("Maksimal 5 file per unggahan.", call(upload("approval_request", requestId,
                file("1.pdf", "application/pdf", PDF), file("2.pdf", "application/pdf", PDF), file("3.pdf", "application/pdf", PDF),
                file("4.pdf", "application/pdf", PDF), file("5.pdf", "application/pdf", PDF), file("6.pdf", "application/pdf", PDF))).error());
        byte[] big = Arrays.copyOf(PDF, (int) ErpAttachmentService.MAX_FILE_SIZE + 1);
        assertEquals("Ukuran file maksimal 5 MB.", call(upload("approval_request", requestId, file("besar.pdf", "application/pdf", big))).error());
        assertEquals("Tidak ada file yang diupload.", call(upload("approval_request", requestId)).error());
        assertEquals("referenceType dan referenceId wajib diisi.", call(upload(null, requestId, file("a.pdf", "application/pdf", PDF))).error());
        assertEquals("referenceType harus salah satu dari: payment, journal, purchase_invoice, sales_invoice, sales_order, "
                + "delivery_order, goods_receipt, approval_request.", call(upload("invoice", requestId, file("a.pdf", "application/pdf", PDF))).error());
        assertEquals(2, ok(get("/api/erp/attachments/approval_request/" + requestId)).size());

        // Pengguna lain: approvals:view boleh melihat, tapi hanya pengaju (atau approval.view_all) yang boleh mengubah.
        login("att2." + u + "@test.local", "ERP_ACCOUNTANT");
        assertEquals(2, ok(get("/api/erp/attachments/approval_request/" + requestId)).size());
        Result foreignUpload = call(upload("approval_request", requestId, file("lain.pdf", "application/pdf", PDF)));
        assertEquals(403, foreignUpload.status());
        assertEquals(DENIED, foreignUpload.error());
        assertEquals(403, call(delete("/api/erp/attachments/" + pdfId)).status());
        Result unknownRequest = call(upload("approval_request", UUID.randomUUID().toString(), file("a.pdf", "application/pdf", PDF)));
        assertEquals(404, unknownRequest.status());
        assertEquals("Pengajuan tidak ditemukan.", unknownRequest.error());

        // Tanpa izin modul approvals sama sekali: tidak bisa melihat maupun mengunduh.
        login("attv." + u + "@test.local", "ERP_VIEWER");
        MvcResult deniedDl = raw(get("/api/erp/attachments/file/" + pdfId));
        assertEquals(403, deniedDl.getResponse().getStatus());
        assertTrue(deniedDl.getResponse().getContentAsString(StandardCharsets.UTF_8).contains(DENIED));
        assertEquals(403, call(get("/api/erp/attachments/approval_request/" + requestId)).status());
        assertEquals(403, call(post("/api/erp/attachments/counts", map("referenceType", "approval_request",
                "referenceIds", List.of(requestId)))).status());

        // Hapus oleh pengaju: baris & berkas hilang.
        asUser(email, "ERP_ACCOUNTANT");
        assertEquals("Lampiran dihapus.", ok(delete("/api/erp/attachments/" + pngId)).get("message").asText());
        assertFalse(Files.exists(storage.resolve(pngPath)));
        assertEquals(404, raw(get("/api/erp/attachments/file/" + pngId)).getResponse().getStatus());
        Result gone = call(delete("/api/erp/attachments/" + pngId));
        assertEquals(404, gone.status());
        assertEquals("Lampiran tidak ditemukan.", gone.error());

        // Setelah diputuskan, lampiran baru harus ke dokumennya.
        loginAdmin();
        ok(post("/api/erp/approvals/requests/" + requestId + "/approve", map()));
        asUser(email, "ERP_ACCOUNTANT");
        assertEquals("Pengajuan sudah diproses; lampirkan ke dokumennya.",
                call(upload("approval_request", requestId, file("telat.pdf", "application/pdf", PDF))).error());
        ok(delete("/api/erp/attachments/" + pdfId));
    }

    @Test
    void document_attachments_follow_module_permissions_and_invoice_related_payments() throws Exception {
        String adminId = loginAdmin();
        String u = uid();
        String supplier = createParty("Supplier Att " + u, "Supplier");
        String piId = createPurchaseInvoice(supplier, "PI-ATT-" + u, "300000", "Submitted", adminId);
        String payId = createPayment("PAY-ATT-" + u, supplier, "Pay", adminId);
        jdbc.update("INSERT INTO payment_allocations (id, payment_id, invoice_type, invoice_id, allocated_amount) "
                + "VALUES (?, ?, 'PurchaseInvoice', ?, 100000)", UUID.randomUUID().toString(), payId, piId);
        String journalId = createJournal("JV-ATT-" + u, adminId);

        assertEquals("Invoice pembelian tidak ditemukan.", call(upload("purchase_invoice", UUID.randomUUID().toString(),
                file("a.pdf", "application/pdf", PDF))).error());
        JsonNode piAtt = ok(upload("purchase_invoice", piId, file("invoice.pdf", "application/pdf", PDF)));
        JsonNode payAtt = ok(upload("payment", payId, file("transfer.png", "image/png", PNG)));
        JsonNode related = ok(get("/api/erp/attachments/invoice/purchase/" + piId + "/related"));
        assertEquals(1, related.get("invoice").size());
        assertEquals("invoice", related.get("invoice").get(0).get("source").asText());
        assertTrue(related.get("invoice").get(0).get("payment").isNull());
        assertTrue(related.get("invoice").get(0).get("user").has("fullName"));
        JsonNode payRow = related.get("payments").get(0);
        assertEquals("payment", payRow.get("source").asText());
        assertEquals("PAY-ATT-" + u, payRow.get("payment").get("paymentNumber").asText());
        assertEquals("Pay", payRow.get("payment").get("paymentType").asText());
        assertEquals("Submitted", payRow.get("payment").get("status").asText());
        assertEquals("type harus 'sales' atau 'purchase'.", call(get("/api/erp/attachments/invoice/other/" + piId + "/related")).error());

        // Viewer: journal view → boleh lihat lampiran jurnal, tidak boleh unggah; tidak punya purchase_invoice.
        login("docv." + u + "@test.local", "ERP_VIEWER");
        assertEquals(0, ok(get("/api/erp/attachments/journal/" + journalId)).size());
        assertEquals(403, call(upload("journal", journalId, file("j.pdf", "application/pdf", PDF))).status());
        assertEquals(403, call(get("/api/erp/attachments/purchase_invoice/" + piId)).status());
        assertEquals(403, raw(get("/api/erp/attachments/file/" + piAtt.get(0).get("id").asText())).getResponse().getStatus());
        assertEquals("referenceType tidak dikenal.", call(get("/api/erp/attachments/unknown/" + piId)).error());

        // Akuntan: purchase_invoice edit → boleh unduh & hapus.
        login("doca." + u + "@test.local", "ERP_ACCOUNTANT");
        assertEquals(200, raw(get("/api/erp/attachments/file/" + piAtt.get(0).get("id").asText())).getResponse().getStatus());
        ok(delete("/api/erp/attachments/" + piAtt.get(0).get("id").asText()));
        ok(delete("/api/erp/attachments/" + payAtt.get(0).get("id").asText()));
        assertEquals(0, ok(get("/api/erp/attachments/invoice/purchase/" + piId + "/related")).get("payments").size());
    }
}
