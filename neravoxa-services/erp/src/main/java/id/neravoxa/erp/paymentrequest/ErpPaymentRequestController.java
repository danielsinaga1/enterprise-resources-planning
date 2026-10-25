package id.neravoxa.erp.paymentrequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.storage.ErpFileStorage;
import id.neravoxa.erp.paymentrequest.ErpPaymentRequestService.PrTransition;
import id.neravoxa.erp.paymentrequest.PrRequests.AttachmentJson;
import id.neravoxa.erp.paymentrequest.PrRequests.Decision;
import id.neravoxa.erp.paymentrequest.PrRequests.Pay;
import id.neravoxa.erp.paymentrequest.PrRequests.PaymentRequestInput;
import id.neravoxa.erp.paymentrequest.PrRequests.Reason;
import id.neravoxa.erp.paymentrequest.PrRequests.SettlementDraft;
import id.neravoxa.erp.paymentrequest.PrRequests.SettlementPost;
import id.neravoxa.erp.paymentrequest.PrRequests.SettlementPreview;
import id.neravoxa.erp.paymentrequest.PrRequests.Verify;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.WebUtils;

import java.util.List;
import java.util.Map;

/**
 * Pengajuan Pembayaran — port {@code routes/paymentRequests.ts} (mount {@code /api/payment-requests}).
 *
 * <p>Dipasang TANPA gerbang modul (ErpRouteGuard: OPEN): setiap pengguna login boleh membuat pengajuan dan melihat
 * miliknya, atasan memutus pengajuan bawahannya. Aksi Keuangan (verifikasi / setujui sebagai Keuangan / bayar /
 * batal bayar) dan tab "Semua" memeriksa izin {@code payment_request} di handler; tahap Direktur memeriksa role
 * snapshot dari ApprovalRule.
 *
 * <p>GL (saat bayar): DR beban per baris (atau DR Uang Muka Karyawan untuk CASH_ADVANCE / TRAVEL) / CR kas-bank.
 * Pertanggungjawaban ({@code /:id/settlement/*}): pengaju mengisi realisasi, atasan menyetujui, Keuangan memverifikasi
 * akun dan memposting DR beban / CR Uang Muka Karyawan (+ kas-bank untuk sisa / kurang bayar).
 */
@RestController
@RequestMapping("/api/erp/payment-requests")
@RequiredArgsConstructor
public class ErpPaymentRequestController {

    private final ErpPaymentRequestSupport support;
    private final ErpPaymentRequestQueryService queries;
    private final ErpPaymentRequestService requests;
    private final ErpPaymentRequestSettlementService settlements;
    private final ErpPaymentRequestAttachmentService attachments;
    private final ErpPaymentRequestNotifier notifier;

    /** Transisi sudah COMMIT saat service kembali → kirim notifikasi lalu kembalikan baris pengajuan. */
    private ObjectNode done(PrTransition t) {
        notifier.dispatch(t.events());
        return t.request();
    }

    /** GET /meta — konteks pengguna untuk formulir & tab. */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return queries.meta(support.viewer());
    }

    /** GET / — daftar berpaginasi per scope (mine | approvals | payable | advances | all). */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return queries.list(support.viewer(), query);
    }

    /** GET /summary — KPI per status untuk scope + filter yang sama, plus badge tab. */
    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return queries.summary(support.viewer(), query);
    }

    /** GET /advances/summary — uang muka karyawan yang belum dipertanggungjawabkan. */
    @GetMapping("/advances/summary")
    public Map<String, Object> advancesSummary() {
        return queries.advancesSummary(support.viewer());
    }

    /** GET /attachments/:attId — unduh / pratinjau (visibilitas sama dengan pengajuannya). */
    @GetMapping("/attachments/{attId}")
    public ResponseEntity<Resource> download(@PathVariable String attId) {
        ErpPaymentRequestAttachmentService.Download d = attachments.download(attId, support.viewer());
        boolean inline = d.mimeType().startsWith("image/") || "application/pdf".equals(d.mimeType());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, d.mimeType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ErpFileStorage.contentDisposition(d.fileName(), inline))
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(d.path()));
    }

    /** GET /:id — detail + aksi yang boleh dilakukan pengguna. */
    @GetMapping("/{id}")
    public ObjectNode detail(@PathVariable String id) {
        return queries.detail(support.viewer(), id);
    }

    /** POST / — draf baru (semua pengguna login). */
    @PostMapping
    public ResponseEntity<ObjectNode> create(@RequestBody(required = false) JsonNode body) {
        PaymentRequestInput input = PaymentRequestInput.parse(body);
        return ResponseEntity.status(201).body(requests.create(support.viewer(), input));
    }

    /** PUT /:id — ubah selama DRAFT / REVISION (hanya pengaju). */
    @PutMapping("/{id}")
    public ObjectNode update(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        PaymentRequestInput input = PaymentRequestInput.parse(body);
        return requests.update(id, support.viewer(), input);
    }

    @PostMapping("/{id}/submit")
    public ObjectNode submit(@PathVariable String id) {
        return done(requests.submit(id, support.viewer()));
    }

    @PostMapping("/{id}/approve")
    public ObjectNode approve(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        Decision input = Decision.parse(body);
        return done(requests.approve(id, support.viewer(), input));
    }

    @PostMapping("/{id}/reject")
    public ObjectNode reject(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        Reason input = Reason.parse(body);
        return done(requests.decline(id, support.viewer(), "REJECT", input.reason()));
    }

    @PostMapping("/{id}/return")
    public ObjectNode returnForRevision(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        Reason input = Reason.parse(body);
        return done(requests.decline(id, support.viewer(), "RETURN", input.reason()));
    }

    /** POST /:id/cancel — alasan opsional tanpa validasi skema (dipotong 1000 karakter). */
    @PostMapping("/{id}/cancel")
    public ObjectNode cancel(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        JsonNode r = body == null ? null : body.get("reason");
        String reason = null;
        if (r != null && r.isTextual()) {
            String t = PrZod.jsTrim(r.textValue());
            reason = t.length() > 1000 ? t.substring(0, 1000) : t;
        }
        return done(requests.cancel(id, support.viewer(), reason));
    }

    /** PUT /:id/accounts — Keuangan mengatur akun biaya per baris. */
    @PutMapping("/{id}/accounts")
    public ObjectNode accounts(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        Verify input = Verify.parse(body);
        return requests.verifyAccounts(id, support.viewer(), input.lineAccounts());
    }

    @PostMapping("/{id}/pay")
    public ObjectNode pay(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        Pay input = Pay.parse(body);
        return done(requests.pay(id, support.viewer(), input));
    }

    @PostMapping("/{id}/cancel-payment")
    public ObjectNode cancelPayment(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        Reason input = Reason.parse(body);
        return done(requests.cancelPayment(id, support.viewer(), input.reason()));
    }

    // ───────────────────────────── pertanggungjawaban ─────────────────────────────

    /** PUT /:id/settlement — pengaju menyimpan baris realisasi (status tidak berubah). */
    @PutMapping("/{id}/settlement")
    public ObjectNode saveSettlement(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        SettlementDraft input = SettlementDraft.parse(body);
        return settlements.saveDraft(id, support.viewer(), input);
    }

    @PostMapping("/{id}/settlement/submit")
    public ObjectNode submitSettlement(@PathVariable String id) {
        return done(settlements.submit(id, support.viewer()));
    }

    /** Persetujuan atasan (tahap Keuangan = /settlement/post). */
    @PostMapping("/{id}/settlement/approve")
    public ObjectNode approveSettlement(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        Decision input = Decision.parse(body);
        return done(settlements.approve(id, support.viewer(), input.note()));
    }

    @PostMapping("/{id}/settlement/return")
    public ObjectNode returnSettlement(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        Reason input = Reason.parse(body);
        return done(settlements.returnForRevision(id, support.viewer(), input.reason()));
    }

    /** POST /:id/settlement/preview — pratinjau DR/CR untuk tahap Keuangan (tidak menyimpan apa pun). */
    @PostMapping("/{id}/settlement/preview")
    public Map<String, Object> previewSettlement(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        SettlementPreview input = SettlementPreview.parse(body);
        return settlements.preview(id, support.viewer(), input.lineAccounts(), input.cashAccountId());
    }

    @PostMapping("/{id}/settlement/post")
    public ObjectNode postSettlement(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        SettlementPost input = SettlementPost.parse(body);
        return done(settlements.post(id, support.viewer(), input));
    }

    @PostMapping("/{id}/settlement/cancel-posting")
    public ObjectNode cancelSettlementPosting(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        Reason input = Reason.parse(body);
        return done(settlements.cancelPosting(id, support.viewer(), input.reason()));
    }

    // ───────────────────────────── lampiran ─────────────────────────────

    /**
     * POST /:id/attachments (?scope=settlement) — multipart field {@code files} (maks. 5 per unggahan, ≤ 5 MB,
     * JPG/PNG/WebP/PDF). Validasi multer berjalan sebelum cek akses, seperti middleware di sumber.
     */
    @PostMapping("/{id}/attachments")
    public ResponseEntity<List<ObjectNode>> upload(@PathVariable String id, @RequestParam Map<String, String> query,
                                                   HttpServletRequest request) {
        MultipartHttpServletRequest multipart = WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
        List<ErpPaymentRequestAttachmentService.IncomingFile> files =
                ErpPaymentRequestAttachmentService.readMultipart(multipart != null ? multipart : request);
        String scope = "settlement".equals(ListQuery.str(query, "scope")) ? "SETTLEMENT" : "REQUEST";
        return ResponseEntity.status(201).body(attachments.upload(id, scope, support.viewer(), files));
    }

    /** POST /:id/attachments/json — lampiran via JSON (isi base64) untuk klien tanpa multipart. */
    @PostMapping("/{id}/attachments/json")
    public ResponseEntity<List<ObjectNode>> uploadJson(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        AttachmentJson input = AttachmentJson.parse(body);
        return ResponseEntity.status(201).body(attachments.uploadJson(id, support.viewer(), input));
    }

    @DeleteMapping("/{id}/attachments/{attId}")
    public Map<String, Object> deleteAttachment(@PathVariable String id, @PathVariable String attId) {
        return attachments.delete(id, attId, support.viewer());
    }
}
