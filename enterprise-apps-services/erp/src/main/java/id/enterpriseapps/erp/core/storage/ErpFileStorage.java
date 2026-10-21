package id.enterpriseapps.erp.core.storage;

import id.enterpriseapps.erp.core.ErpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Penyimpanan berkas ERP — port {@code utils/fileStorage.ts}.
 *
 * <ul>
 *   <li><b>Privat</b> ({@link #save}): lampiran transaksi/pengajuan (dokumen keuangan). Disimpan di
 *       {@code app.erp.storage-dir} (default {@code ./erp-files}) — DI LUAR folder {@code uploads/}
 *       yang disajikan publik oleh platform — dan hanya diunduh lewat endpoint ber-izin.</li>
 *   <li><b>Publik</b> ({@link #savePublic}): foto aset/kendaraan yang juga tampil di halaman scan QR
 *       tanpa login. Disimpan di {@code ./uploads/erp/...} dan diakses lewat URL {@code /uploads/erp/...}.</li>
 * </ul>
 * Kompresi gambar (sharp) di sumber tidak dipindahkan: berkas disimpan apa adanya.
 */
@Slf4j
@Service
public class ErpFileStorage {

    /** Tipe yang diterima untuk lampiran transaksi / pengajuan pembayaran. */
    public static final List<String> ATTACHMENT_MIME = List.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    private final Path privateRoot;
    private final Path publicRoot;

    public ErpFileStorage(@Value("${app.erp.storage-dir:}") String storageDir) {
        String base = storageDir == null || storageDir.isBlank()
                ? Paths.get(System.getProperty("user.dir"), "erp-files").toString() : storageDir;
        this.privateRoot = Paths.get(base).toAbsolutePath().normalize();
        this.publicRoot = Paths.get(System.getProperty("user.dir"), "uploads", "erp").toAbsolutePath().normalize();
    }

    public record SaveResult(String storedName, String filePath, long fileSize, String mimeType) {
    }

    /** Simpan berkas privat di {@code <root>/<sub>/YYYY/MM/<uuid>.<ext>}; filePath relatif terhadap root. */
    public SaveResult save(byte[] bytes, String originalName, String mimeType, String sub) {
        return write(privateRoot, bytes, originalName, mimeType, sub);
    }

    /**
     * Simpan berkas publik (foto aset/kendaraan). {@code filePath} hasilnya RELATIF terhadap folder
     * {@code uploads/} platform dan sudah diawali {@code erp/} (mis. {@code erp/ga/assets/2026/10/x.jpg}),
     * sehingga UI ENTARIS yang menampilkan {@code /uploads/${filePath}} tetap benar.
     */
    public SaveResult savePublic(byte[] bytes, String originalName, String mimeType, String sub) {
        SaveResult r = write(publicRoot, bytes, originalName, mimeType, sub);
        return new SaveResult(r.storedName(), "erp/" + r.filePath(), r.fileSize(), r.mimeType());
    }

    /** URL publik untuk berkas dari {@link #savePublic}. */
    public static String publicUrl(String filePath) {
        return "/uploads/" + filePath.replace('\\', '/');
    }

    private SaveResult write(Path root, byte[] bytes, String originalName, String mimeType, String sub) {
        LocalDate now = LocalDate.now();
        String subDir = (sub == null || sub.isBlank() ? "" : sanitize(sub) + "/")
                + now.getYear() + "/" + String.format("%02d", now.getMonthValue());
        String ext = extension(originalName, mimeType);
        String storedName = UUID.randomUUID() + "." + ext;
        String rel = subDir + "/" + storedName;
        try {
            Path dir = root.resolve(subDir).normalize();
            Files.createDirectories(dir);
            Files.write(dir.resolve(storedName), bytes);
        } catch (IOException e) {
            log.error("ERP: gagal menyimpan berkas {}", rel, e);
            throw ErpException.of(500, "Gagal menyimpan file.");
        }
        return new SaveResult(storedName, rel, bytes.length, mimeType);
    }

    /** Path absolut berkas privat (dengan proteksi path traversal). */
    public Path resolve(String filePath) {
        return safe(privateRoot, filePath);
    }

    /** Path absolut berkas publik; menerima filePath dengan atau tanpa prefix {@code erp/}. */
    public Path resolvePublic(String filePath) {
        String rel = filePath.replace('\\', '/');
        if (rel.startsWith("erp/")) {
            rel = rel.substring(4);
        }
        return safe(publicRoot, rel);
    }

    private static Path safe(Path root, String filePath) {
        Path p = root.resolve(filePath.replace('\\', '/')).normalize();
        if (!p.startsWith(root)) {
            throw ErpException.business("Path file tidak valid.");
        }
        return p;
    }

    public void delete(String filePath) {
        try {
            Files.delete(resolve(filePath));
        } catch (NoSuchFileException ignored) {
            // sudah tidak ada
        } catch (IOException e) {
            log.error("ERP: gagal menghapus berkas {}", filePath, e);
            throw ErpException.of(500, "Gagal menghapus file.");
        }
    }

    public void deletePublic(String filePath) {
        try {
            Files.deleteIfExists(resolvePublic(filePath));
        } catch (IOException e) {
            log.warn("ERP: gagal menghapus berkas publik {}: {}", filePath, e.getMessage());
        }
    }

    /** Deteksi MIME dari magic bytes (JPEG/PNG/WebP/PDF); null bila tidak dikenal. */
    public static String detectMimeFromMagicBytes(byte[] b) {
        if (b == null || b.length < 4) {
            return null;
        }
        if ((b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 0x50 && b[2] == 0x4e && b[3] == 0x47
                && b[4] == 0x0d && b[5] == 0x0a && b[6] == 0x1a && b[7] == 0x0a) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46
                && b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50) {
            return "image/webp";
        }
        if (b[0] == 0x25 && b[1] == 0x50 && b[2] == 0x44 && b[3] == 0x46) {
            return "application/pdf";
        }
        return null;
    }

    /** Header Content-Disposition (RFC 6266). */
    public static String contentDisposition(String fileName, boolean inline) {
        if (inline) {
            return "inline";
        }
        String ascii = fileName.replaceAll("[^\\x20-\\x7E]", "_").replaceAll("[\"\\\\\\r\\n]", "_");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String extension(String originalName, String mimeType) {
        if ("application/pdf".equals(mimeType)) {
            return "pdf";
        }
        if ("image/jpeg".equals(mimeType)) {
            return "jpg";
        }
        if ("image/png".equals(mimeType)) {
            return "png";
        }
        if ("image/webp".equals(mimeType)) {
            return "webp";
        }
        if (originalName != null && originalName.contains(".")) {
            String e = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
            if (e.matches("[a-z0-9]{1,8}")) {
                return e;
            }
        }
        return "bin";
    }

    private static String sanitize(String sub) {
        return sub.replaceAll("[^a-zA-Z0-9/_-]", "_").replaceAll("\\.\\.", "_");
    }
}
