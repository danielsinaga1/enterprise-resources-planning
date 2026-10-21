package id.enterpriseapps.erp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.security.ErpAccess;
import id.enterpriseapps.erp.core.storage.ErpFileStorage;
import id.enterpriseapps.erp.entity.DeliveryOrder;
import id.enterpriseapps.erp.entity.GoodsReceipt;
import id.enterpriseapps.erp.entity.JournalEntry;
import id.enterpriseapps.erp.entity.Payment;
import id.enterpriseapps.erp.entity.PurchaseInvoice;
import id.enterpriseapps.erp.entity.SalesInvoice;
import id.enterpriseapps.erp.entity.SalesOrder;
import id.enterpriseapps.erp.entity.TransactionAttachment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lampiran transaksi (bukti transfer, nota, scan dokumen) — port {@code routes/attachments.ts}.
 *
 * <p>Berkas disimpan PRIVAT lewat {@link ErpFileStorage#save} (di luar folder publik) dan hanya bisa
 * diunduh lewat {@code GET /attachments/file/:id} setelah cek akses dokumen induk. Maks. 5 berkas per
 * dokumen, 5 MB per berkas, JPG/PNG/WebP/PDF yang diverifikasi dari magic bytes (Content-Type klien
 * mudah dipalsukan).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpAttachmentService {

    static final int MAX_FILES = 5;
    static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final ErpAttachmentAccessService accessService;
    private final ErpAccess access;
    private final ErpFileStorage storage;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Berkas siap unduh. */
    public record FileDownload(Path path, String mimeType, String contentDisposition) {
    }

    /** {@code POST /upload} (multipart: referenceType, referenceId, files[]) → 201 daftar baris lampiran baru. */
    @ErpTx
    public List<ObjectNode> upload(String referenceType, String referenceId, List<MultipartFile> rawFiles) {
        List<MultipartFile> files = rawFiles == null ? List.of() : rawFiles.stream().filter(Objects::nonNull).toList();
        // Batas yang di sumber dijaga multer sebelum handler: jumlah, tipe (Content-Type klien), ukuran.
        if (files.size() > MAX_FILES) {
            throw ErpException.business("Maksimal 5 file per unggahan.");
        }
        for (MultipartFile f : files) {
            String mime = mimeOf(f);
            if (!ErpFileStorage.ATTACHMENT_MIME.contains(mime)) {
                throw ErpException.business("Tipe file tidak didukung: " + mime + ". Hanya JPG, PNG, WebP, dan PDF.");
            }
            if (f.getSize() > MAX_FILE_SIZE) {
                throw ErpException.business("Ukuran file maksimal 5 MB.");
            }
        }

        if (referenceType == null || referenceType.isEmpty() || referenceId == null || referenceId.isEmpty()) {
            throw ErpException.business("referenceType dan referenceId wajib diisi.");
        }
        if (!ErpAttachmentAccessService.isRefType(referenceType)) {
            throw ErpException.business("referenceType harus salah satu dari: "
                    + String.join(", ", ErpAttachmentAccessService.REF_TYPES) + ".");
        }
        // Unggah butuh `edit` pada modul dokumen (lampiran pengajuan: pengajunya).
        accessService.require(referenceType, referenceId, ErpAttachmentAccessService.EDIT);
        requireReference(referenceType, referenceId);

        long existing = em.createQuery("select count(a) from TransactionAttachment a where a.referenceType = :t and a.referenceId = :id",
                        Long.class).setParameter("t", referenceType).setParameter("id", referenceId).getSingleResult();
        if (files.isEmpty()) {
            throw ErpException.business("Tidak ada file yang diupload.");
        }
        if (existing + files.size() > MAX_FILES) {
            throw ErpException.business("Maksimal " + MAX_FILES + " file per transaksi. Saat ini sudah ada " + existing + " file.");
        }

        List<byte[]> contents = new ArrayList<>();
        for (MultipartFile f : files) {
            byte[] bytes = bytesOf(f);
            String detected = ErpFileStorage.detectMimeFromMagicBytes(bytes);
            String name = nameOf(f);
            if (detected == null || !ErpFileStorage.ATTACHMENT_MIME.contains(detected)) {
                throw ErpException.business("File \"" + name + "\" tidak dikenali sebagai JPG/PNG/WebP/PDF (magic-byte mismatch).");
            }
            if (!detected.equals(mimeOf(f))) {
                throw ErpException.business("File \"" + name + "\" Content-Type (" + mimeOf(f)
                        + ") tidak cocok dengan isi file (" + detected + ").");
            }
            contents.add(bytes);
        }

        // Berkas ditulis sebelum baris DB; bila transaksi batal, berkas yang sudah tertulis dihapus lagi.
        List<String> written = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    written.forEach(ErpAttachmentService.this::deleteQuietly);
                }
            }
        });
        List<TransactionAttachment> created = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile f = files.get(i);
            ErpFileStorage.SaveResult saved = storage.save(contents.get(i), nameOf(f), mimeOf(f), null);
            written.add(saved.filePath());
            TransactionAttachment a = new TransactionAttachment();
            a.setReferenceType(referenceType);
            a.setReferenceId(referenceId);
            a.setFileName(nameOf(f));
            a.setStoredName(saved.storedName());
            a.setMimeType(saved.mimeType());
            a.setFileSize((int) saved.fileSize());
            a.setFilePath(saved.filePath());
            a.setUploadedBy(access.userId());
            em.persist(a);
            created.add(a);
        }
        em.flush();
        return AdminShape.scalars(created);
    }

    /** Dokumen induk harus ada; lampiran pengajuan hanya selagi masih bisa diputuskan/direvisi. */
    private void requireReference(String referenceType, String referenceId) {
        switch (referenceType) {
            case "payment" -> exists(Payment.class, referenceId, "Transaksi pembayaran tidak ditemukan.");
            case "journal" -> exists(JournalEntry.class, referenceId, "Jurnal tidak ditemukan.");
            case "purchase_invoice" -> exists(PurchaseInvoice.class, referenceId, "Invoice pembelian tidak ditemukan.");
            case "sales_invoice" -> exists(SalesInvoice.class, referenceId, "Invoice penjualan tidak ditemukan.");
            case "sales_order" -> exists(SalesOrder.class, referenceId, "Pesanan (SO) tidak ditemukan.");
            case "delivery_order" -> exists(DeliveryOrder.class, referenceId, "Surat Jalan tidak ditemukan.");
            case "goods_receipt" -> exists(GoodsReceipt.class, referenceId, "STT/BPB tidak ditemukan.");
            case "approval_request" -> {
                List<String> status = em.createQuery("select r.status from ApprovalRequest r where r.id = :id", String.class)
                        .setParameter("id", referenceId).getResultList();
                if (status.isEmpty()) {
                    throw ErpException.notFound("Pengajuan tidak ditemukan.");
                }
                if (!List.of("Pending", "NeedsRevision").contains(status.get(0))) {
                    throw ErpException.business("Pengajuan sudah diproses; lampirkan ke dokumennya.");
                }
            }
            default -> {
                // tipe lain sudah ditolak sebelum titik ini
            }
        }
    }

    private void exists(Class<?> entity, String id, String message) {
        Long n = em.createQuery("select count(x) from " + entity.getSimpleName() + " x where x.id = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (n == 0) {
            throw ErpException.notFound(message);
        }
    }

    /** {@code GET /file/:id} — membaca berkas butuh {@code view} pada modul dokumennya. */
    @ErpTx(readOnly = true)
    public FileDownload file(String id) {
        TransactionAttachment a = em.find(TransactionAttachment.class, id);
        if (a == null) {
            throw ErpException.notFound("File tidak ditemukan.");
        }
        accessService.require(a.getReferenceType(), a.getReferenceId(), ErpAttachmentAccessService.VIEW);
        Path path = storage.resolve(a.getFilePath());
        if (!Files.isRegularFile(path)) {
            throw ErpException.notFound("File tidak ditemukan.");
        }
        boolean image = a.getMimeType().startsWith("image/");
        return new FileDownload(path, a.getMimeType(), ErpFileStorage.contentDisposition(a.getFileName(), image));
    }

    /**
     * {@code GET /invoice/:type/:id/related} — lampiran invoice + lampiran semua pembayaran yang
     * dialokasikan ke invoice itu (termasuk pembayaran uang muka yang diaplikasikan), dengan info pembayarannya.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> related(String invoiceType, String invoiceId) {
        boolean sales = "sales".equals(invoiceType);
        if (!sales && !"purchase".equals(invoiceType)) {
            throw ErpException.business("type harus 'sales' atau 'purchase'.");
        }
        String refType = sales ? "sales_invoice" : "purchase_invoice";
        String allocType = sales ? "SalesInvoice" : "PurchaseInvoice";
        // Bukti pembayaran bagian dari detail invoice: cukup izin lihat invoice.
        accessService.require(refType, invoiceId, ErpAttachmentAccessService.VIEW);

        List<ObjectNode> own = withUploader(refType, List.of(invoiceId));
        Set<String> paymentIds = new LinkedHashSet<>(em.createQuery(
                        "select a.paymentId from PaymentAllocation a where a.invoiceType = :t and a.invoiceId = :id", String.class)
                .setParameter("t", allocType).setParameter("id", invoiceId).getResultList());
        paymentIds.addAll(em.createQuery(sales
                        ? "select d.depositPaymentId from CustomerDepositApplication d where d.salesInvoiceId = :id and d.isCancelled = false"
                        : "select d.depositPaymentId from VendorDepositApplication d where d.purchaseInvoiceId = :id and d.isCancelled = false",
                String.class).setParameter("id", invoiceId).getResultList());

        List<ObjectNode> paymentAtt = paymentIds.isEmpty() ? List.of() : withUploader("payment", paymentIds);
        Map<String, Map<String, Object>> meta = new LinkedHashMap<>();
        if (!paymentIds.isEmpty()) {
            for (Object[] p : em.createQuery("select p.id, p.paymentNumber, p.paymentType, p.date, p.status from Payment p "
                    + "where p.id in :ids", Object[].class).setParameter("ids", paymentIds).getResultList()) {
                meta.put((String) p[0], ErpJson.map("id", p[0], "paymentNumber", p[1], "paymentType", p[2], "date", p[3],
                        "status", p[4]));
            }
        }
        List<ObjectNode> invoice = new ArrayList<>();
        for (ObjectNode a : own) {
            a.put("source", "invoice");
            a.putNull("payment");
            invoice.add(a);
        }
        List<ObjectNode> payments = new ArrayList<>();
        for (ObjectNode a : paymentAtt) {
            a.put("source", "payment");
            a.set("payment", ErpJson.tree(meta.get(a.path("referenceId").asText())));
            payments.add(a);
        }
        return ErpJson.map("invoice", invoice, "payments", payments);
    }

    /** {@code GET /:referenceType/:referenceId} — daftar lampiran dokumen (+ uploader). */
    @ErpTx(readOnly = true)
    public List<ObjectNode> list(String referenceType, String referenceId) {
        if (!ErpAttachmentAccessService.isRefType(referenceType)) {
            throw ErpException.business("referenceType tidak dikenal.");
        }
        accessService.require(referenceType, referenceId, ErpAttachmentAccessService.VIEW);
        return withUploader(referenceType, List.of(referenceId));
    }

    /** {@code DELETE /:id} — butuh {@code edit} pada modul dokumen lampiran. Berkas dihapus setelah COMMIT. */
    @ErpTx
    public void delete(String id) {
        TransactionAttachment a = em.find(TransactionAttachment.class, id);
        if (a == null) {
            throw ErpException.notFound("Lampiran tidak ditemukan.");
        }
        accessService.require(a.getReferenceType(), a.getReferenceId(), ErpAttachmentAccessService.EDIT);
        String filePath = a.getFilePath();
        em.remove(a);
        em.flush();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(filePath);
            }
        });
    }

    /** {@code POST /counts} — jumlah lampiran per dokumen untuk indikator di daftar. */
    @ErpTx(readOnly = true)
    public Map<String, Object> counts(JsonNode body) {
        JsonNode typeNode = body == null ? null : body.get("referenceType");
        JsonNode idsNode = body == null ? null : body.get("referenceIds");
        Map<String, Object> out = new LinkedHashMap<>();
        if (!AdminJs.truthy(typeNode) || idsNode == null || !idsNode.isArray() || idsNode.isEmpty()) {
            return out;
        }
        String referenceType = typeNode.isTextual() ? typeNode.textValue() : null;
        if (!ErpAttachmentAccessService.isRefType(referenceType)) {
            throw ErpException.business("referenceType tidak dikenal.");
        }
        // Hitungan massal: izin lihat modul tanpa cek per dokumen.
        accessService.require(referenceType, null, ErpAttachmentAccessService.VIEW);
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode v : idsNode) {
            if (v != null && v.isValueNode() && !v.isNull()) {
                ids.add(v.asText());
            }
        }
        if (ids.isEmpty()) {
            return out;
        }
        for (Object[] r : em.createQuery("select a.referenceId, count(a) from TransactionAttachment a "
                        + "where a.referenceType = :t and a.referenceId in :ids group by a.referenceId", Object[].class)
                .setParameter("t", referenceType).setParameter("ids", ids).getResultList()) {
            out.put((String) r[0], ((Number) r[1]).longValue());
        }
        return out;
    }

    // ───────────────────────── helper ─────────────────────────

    /** Baris lampiran (semua kolom) + {@code user: { fullName }}, urut waktu unggah. */
    private List<ObjectNode> withUploader(String referenceType, Collection<String> referenceIds) {
        List<Object[]> rows = em.createQuery("select a, u.fullName from TransactionAttachment a left join a.user u "
                        + "where a.referenceType = :t and a.referenceId in :ids order by a.createdAt asc", Object[].class)
                .setParameter("t", referenceType).setParameter("ids", referenceIds).getResultList();
        List<ObjectNode> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            ObjectNode o = AdminShape.scalars(r[0]);
            o.set("user", ErpJson.tree(r[1] == null ? null : ErpJson.map("fullName", r[1])));
            out.add(o);
        }
        return out;
    }

    private void deleteQuietly(String filePath) {
        try {
            storage.delete(filePath);
        } catch (RuntimeException e) {
            log.warn("ERP: gagal menghapus berkas lampiran {}: {}", filePath, e.getMessage());
        }
    }

    private static String mimeOf(MultipartFile f) {
        String ct = f.getContentType();
        return ct == null || ct.isBlank() ? "application/octet-stream" : ct;
    }

    private static String nameOf(MultipartFile f) {
        String n = f.getOriginalFilename();
        return n == null ? "" : n;
    }

    private static byte[] bytesOf(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw ErpException.of(500, "Gagal mengupload file.");
        }
    }
}
