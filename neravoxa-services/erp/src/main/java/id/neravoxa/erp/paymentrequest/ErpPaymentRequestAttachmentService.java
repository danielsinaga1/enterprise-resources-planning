package id.neravoxa.erp.paymentrequest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.storage.ErpFileStorage;
import id.neravoxa.erp.entity.PaymentRequest;
import id.neravoxa.erp.entity.PaymentRequestAttachment;
import id.neravoxa.erp.paymentrequest.PrRequests.AttachmentJson;
import id.neravoxa.erp.paymentrequest.PrRules.Check;
import id.neravoxa.erp.paymentrequest.PrRules.Ref;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Lampiran Pengajuan Pembayaran — port {@code services/paymentRequestAttachments.ts} + rute lampiran di
 * {@code routes/paymentRequests.ts}. Berkas disimpan PRIVAT ({@link ErpFileStorage#save}) dan hanya diunduh lewat
 * endpoint dengan visibilitas yang sama seperti pengajuannya.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpPaymentRequestAttachmentService {

    static final long PR_MAX_FILE_SIZE = 5L * 1024 * 1024;
    static final int PR_MAX_FILES_PER_REQUEST = 10;
    /** Batas multer per unggahan ({@code upload.array('files', 5)}). */
    static final int MAX_FILES_PER_UPLOAD = 5;
    static final String FILE_NOT_FOUND = "File tidak ditemukan.";
    private static final String STORAGE_SUB = "payment-requests";

    private final ErpPaymentRequestSupport support;
    private final ErpFileStorage storage;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Berkas masuk (padanan {@code IncomingPrFile}). */
    record IncomingFile(byte[] bytes, String originalName, String mimeType) {
    }

    /** Berkas untuk diunduh. */
    record Download(Path path, String fileName, String mimeType) {
    }

    /**
     * Padanan middleware multer {@code uploadFiles}: field {@code files} (maks. 5), tipe JPG/PNG/WebP/PDF, ≤ 5 MB.
     * Dijalankan SEBELUM cek akses — sama seperti middleware di sumber.
     */
    static List<IncomingFile> readMultipart(HttpServletRequest request) {
        if (!(request instanceof MultipartHttpServletRequest mr)) {
            return List.of();
        }
        for (Map.Entry<String, List<MultipartFile>> e : mr.getMultiFileMap().entrySet()) {
            if (!"files".equals(e.getKey()) && !e.getValue().isEmpty()) {
                throw ErpException.business("Maksimal 5 file per unggahan.");
            }
        }
        List<MultipartFile> files = mr.getMultiFileMap().getOrDefault("files", List.of());
        List<IncomingFile> out = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            if (i >= MAX_FILES_PER_UPLOAD) {
                throw ErpException.business("Maksimal 5 file per unggahan.");
            }
            MultipartFile f = files.get(i);
            String mime = f.getContentType() == null ? "application/octet-stream" : f.getContentType();
            if (!ErpFileStorage.ATTACHMENT_MIME.contains(mime)) {
                throw ErpException.business("Tipe file tidak didukung: " + mime + ". Hanya JPG, PNG, WebP, dan PDF.");
            }
            if (f.getSize() > PR_MAX_FILE_SIZE) {
                throw ErpException.business("Ukuran file maksimal 5 MB.");
            }
            try {
                out.add(new IncomingFile(f.getBytes(), f.getOriginalFilename() == null ? "" : f.getOriginalFilename(), mime));
            } catch (IOException e) {
                throw ErpException.business("Gagal mengupload lampiran.");
            }
        }
        return out;
    }

    /** Nama berkas aman: tanpa path & karakter kontrol, panjang terbatas. */
    static String cleanFileName(String name) {
        String n = name == null ? "" : name;
        String[] parts = n.split("[\\\\/]", -1);
        String base = parts.length == 0 ? "lampiran" : parts[parts.length - 1];
        String clean = PrZod.jsTrim(base.replaceAll("[\\u0000-\\u001f]", ""));
        String out = clean.isEmpty() ? "lampiran" : clean;
        return out.length() > 255 ? out.substring(0, 255) : out;
    }

    private static Check editableFor(String scope, PaymentRequest pr, String actorId) {
        Ref ref = Ref.of(pr);
        return "SETTLEMENT".equals(scope) ? PrSettlementRules.checkSettlementEditable(ref, actorId)
                : PrRules.checkEditable(ref, actorId);
    }

    /** POST /:id/attachments (?scope=settlement) — pengaju, selama DRAFT/REVISION (atau OPEN/REVISION pertanggungjawaban). */
    @ErpTx
    public List<ObjectNode> upload(String id, String scope, PrViewer ctx, List<IncomingFile> files) {
        PaymentRequest pr = support.visibleRequest(id, ctx);
        Check editable = editableFor(scope, pr, ctx.id());
        if (!editable.ok()) {
            throw ErpException.business(editable.error());
        }
        return addAttachments(id, scope, ctx.id(), files);
    }

    /**
     * Jalur JSON (tanpa multipart): isi base64 kecil. Rujukan lampiran panel chat (AIRA/MCP) tidak ada di ERP,
     * jadi jalur itu berakhir dengan pesan "tidak ditemukan / tidak ada konteks chat" yang sama seperti sumber.
     */
    @ErpTx
    public List<ObjectNode> uploadJson(String id, PrViewer ctx, AttachmentJson body) {
        PaymentRequest pr = support.visibleRequest(id, ctx);
        String scope = "settlement".equals(body.scope()) ? "SETTLEMENT" : "REQUEST";
        Check editable = editableFor(scope, pr, ctx.id());
        if (!editable.ok()) {
            throw ErpException.business(editable.error());
        }
        if (body.contentBase64() == null) {
            if (body.chatAttachmentId() != null) {
                throw ErpException.business("Lampiran chat tidak ditemukan.");
            }
            throw ErpException.business("Tidak ada konteks chat. Sertakan chatAttachmentId atau kirim berkas lewat panel chat Entaris.");
        }
        IncomingFile file = fileFromBase64(body.fileName() != null ? body.fileName() : "lampiran", body.contentBase64(),
                body.mimeType());
        return addAttachments(id, scope, ctx.id(), List.of(file));
    }

    /** Berkas dari isi base64. Jenis ditentukan dari magic byte; mimeType klien hanya petunjuk. */
    static IncomingFile fileFromBase64(String fileName, String contentBase64, String mimeType) {
        String raw = contentBase64.replaceFirst("^data:[^;]+;base64,", "").replaceAll("\\s+", "");
        if (raw.isEmpty() || !raw.matches("^[A-Za-z0-9+/]+={0,2}$")) {
            throw ErpException.business("contentBase64 bukan base64 yang valid.");
        }
        byte[] buffer = decodeLenient(raw);
        String detected = ErpFileStorage.detectMimeFromMagicBytes(buffer);
        if (detected == null || !ErpFileStorage.ATTACHMENT_MIME.contains(detected)) {
            throw ErpException.business("File \"" + fileName + "\" tidak dikenali sebagai JPG/PNG/WebP/PDF.");
        }
        if (mimeType != null && !mimeType.equals(detected)) {
            throw ErpException.business("mimeType \"" + mimeType + "\" tidak cocok dengan isi berkas (terdeteksi " + detected + ").");
        }
        return new IncomingFile(buffer, fileName, detected);
    }

    /** {@code Buffer.from(s, 'base64')} Node: padding opsional, sisa 6 bit diabaikan. */
    private static byte[] decodeLenient(String raw) {
        String s = raw.replaceAll("=+$", "");
        if (s.length() % 4 == 1) {
            s = s.substring(0, s.length() - 1);
        }
        while (s.length() % 4 != 0) {
            s = s + "=";
        }
        return Base64.getDecoder().decode(s);
    }

    /**
     * Validasi (jumlah, ukuran, jenis via magic byte) lalu simpan. Pemanggil sudah memeriksa akses & status. Tidak
     * menyimpan apa pun bila ada yang tidak valid; berkas yang sudah tertulis dihapus lagi bila gagal di tengah.
     */
    private List<ObjectNode> addAttachments(String requestId, String scope, String uploadedBy, List<IncomingFile> files) {
        if (files.isEmpty()) {
            throw ErpException.business("Tidak ada file yang diupload.");
        }
        long existing = em.createQuery("select count(a) from PaymentRequestAttachment a "
                        + "where a.paymentRequestId = :id and a.scope = :scope", Long.class)
                .setParameter("id", requestId).setParameter("scope", scope).getSingleResult();
        if (existing + files.size() > PR_MAX_FILES_PER_REQUEST) {
            throw ErpException.business("Maksimal " + PR_MAX_FILES_PER_REQUEST + " lampiran per pengajuan.");
        }
        for (IncomingFile f : files) {
            if (f.bytes().length == 0) {
                throw ErpException.business("File \"" + f.originalName() + "\" kosong.");
            }
            if (f.bytes().length > PR_MAX_FILE_SIZE) {
                throw ErpException.business("Ukuran file maksimal 5 MB.");
            }
            String detected = ErpFileStorage.detectMimeFromMagicBytes(f.bytes());
            if (detected == null || !detected.equals(f.mimeType())) {
                throw ErpException.business("File \"" + f.originalName() + "\" tidak dikenali sebagai JPG/PNG/WebP/PDF.");
            }
        }
        List<String> written = new ArrayList<>();
        List<PaymentRequestAttachment> created = new ArrayList<>();
        try {
            for (IncomingFile f : files) {
                ErpFileStorage.SaveResult saved = storage.save(f.bytes(), f.originalName(), f.mimeType(), STORAGE_SUB);
                written.add(saved.filePath());
                PaymentRequestAttachment a = new PaymentRequestAttachment();
                a.setPaymentRequestId(requestId);
                a.setFileName(cleanFileName(f.originalName()));
                a.setStoredName(saved.storedName());
                a.setMimeType(saved.mimeType());
                a.setFileSize((int) saved.fileSize());
                a.setFilePath(saved.filePath());
                a.setUploadedBy(uploadedBy);
                a.setScope(scope);
                em.persist(a);
                created.add(a);
            }
            em.flush();
        } catch (RuntimeException e) {
            for (String p : written) {
                deleteQuietly(p);
            }
            throw e;
        }
        registerRollbackCleanup(written);
        return created.stream().map(PrJson::scalars).toList();
    }

    /** Bila transaksi akhirnya rollback, berkas yang sudah tertulis tidak boleh tertinggal. */
    private void registerRollbackCleanup(List<String> written) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    written.forEach(ErpPaymentRequestAttachmentService.this::deleteQuietly);
                }
            }
        });
    }

    private void deleteQuietly(String filePath) {
        try {
            storage.delete(filePath);
        } catch (RuntimeException ex) {
            log.warn("attachment file delete failed path={}: {}", filePath, ex.getMessage());
        }
    }

    /** DELETE /:id/attachments/:attId — aturan ubah mengikuti scope lampiran. */
    @ErpTx
    public Map<String, Object> delete(String id, String attId, PrViewer ctx) {
        PaymentRequest pr = support.visibleRequest(id, ctx);
        List<PaymentRequestAttachment> rows = em.createQuery("select a from PaymentRequestAttachment a "
                        + "where a.id = :att and a.paymentRequestId = :id", PaymentRequestAttachment.class)
                .setParameter("att", attId).setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.notFound(FILE_NOT_FOUND);
        }
        PaymentRequestAttachment att = rows.get(0);
        Check editable = editableFor(att.getScope(), pr, ctx.id());
        if (!editable.ok()) {
            throw ErpException.business(editable.error());
        }
        String filePath = att.getFilePath();
        em.remove(att);
        em.flush();
        // Berkas dihapus setelah COMMIT; kegagalan hanya dicatat (baris sudah hilang).
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(filePath);
                }
            });
        } else {
            deleteQuietly(filePath);
        }
        return Map.of("ok", true);
    }

    /** GET /attachments/:attId — unduh/pratinjau dengan visibilitas yang sama seperti pengajuannya. */
    @ErpTx(readOnly = true)
    public Download download(String attId, PrViewer ctx) {
        PaymentRequestAttachment att = attId == null ? null : em.find(PaymentRequestAttachment.class, attId);
        if (att == null) {
            throw ErpException.notFound(FILE_NOT_FOUND);
        }
        support.visibleRequest(att.getPaymentRequestId(), ctx);
        Path path = storage.resolve(att.getFilePath());
        if (!Files.isRegularFile(path)) {
            throw ErpException.notFound(FILE_NOT_FOUND);
        }
        return new Download(path, att.getFileName(), att.getMimeType());
    }
}
