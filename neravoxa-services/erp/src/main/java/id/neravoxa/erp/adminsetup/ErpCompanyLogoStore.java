package id.neravoxa.erp.adminsetup;

import id.neravoxa.erp.core.storage.ErpFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Penyimpanan logo perusahaan.
 *
 * <p>UI ENTARIS mengirim logo sebagai data URL base64 di {@code PUT /settings/company} (hingga ~4MB)
 * dan menampilkan {@code logoUrl} apa adanya (form, sidebar, PDF, halaman inquiry publik). Agar baris
 * pengaturan, respons {@code GET /settings/company} (dimuat setiap kali aplikasi dibuka) dan jejak audit
 * tidak membawa megabyte base64, logo raster (PNG/JPEG/WebP, dideteksi dari magic bytes) disimpan
 * sebagai berkas publik {@code /uploads/erp/settings/logo/...} dan URL itulah yang disimpan.
 * Nilai lain (SVG, URL eksternal, URL yang sudah tersimpan) disimpan apa adanya seperti sumber — SVG
 * sengaja TIDAK ditulis ke folder publik karena bisa berisi skrip bila dibuka langsung; sebagai data URL
 * di {@code <img>} ia aman.
 */
@Slf4j
@Component
public class ErpCompanyLogoStore {

    static final String SUB = "settings/logo";
    /** Hanya berkas yang dibuat kelas ini yang boleh dihapus (bukan foto aset dsb.). */
    private static final Pattern MANAGED = Pattern.compile(
            "^/uploads/erp/settings/logo/\\d{4}/\\d{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp)$");

    private final ErpFileStorage storage;

    public ErpCompanyLogoStore(ErpFileStorage storage) {
        this.storage = storage;
    }

    /**
     * Nilai yang disimpan untuk {@code logoUrl}/{@code logoUrlDark}: data URL raster → URL berkas publik
     * baru (dihapus lagi bila transaksi gagal); selain itu apa adanya.
     */
    String store(String value) {
        if (value == null || !value.startsWith("data:")) {
            return value;
        }
        // data:[<mime>][;param=...];base64,<payload> — header dipisah manual (string bisa ~4MB).
        int comma = value.indexOf(',');
        if (comma < 0 || !value.substring(0, comma).endsWith(";base64")) {
            return value;
        }
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(value.substring(comma + 1));
        } catch (IllegalArgumentException e) {
            return value;
        }
        String mime = ErpFileStorage.detectMimeFromMagicBytes(bytes);
        if (mime == null || !mime.startsWith("image/")) {
            return value;
        }
        ErpFileStorage.SaveResult saved = storage.savePublic(bytes, "logo", mime, SUB);
        AfterCommit.onRollback(() -> storage.deletePublic(saved.filePath()));
        log.info("ERP: logo perusahaan disimpan sebagai {}", saved.filePath());
        return ErpFileStorage.publicUrl(saved.filePath());
    }

    /** Hapus berkas logo lama setelah commit bila sudah tidak dipakai kedua slot logo. */
    void discardAfterCommit(String oldValue, String currentLight, String currentDark) {
        if (!isManaged(oldValue) || oldValue.equals(currentLight) || oldValue.equals(currentDark)) {
            return;
        }
        String filePath = oldValue.substring("/uploads/".length());
        AfterCommit.run(() -> storage.deletePublic(filePath));
    }

    static boolean isManaged(String value) {
        return value != null && MANAGED.matcher(value).matches();
    }
}
