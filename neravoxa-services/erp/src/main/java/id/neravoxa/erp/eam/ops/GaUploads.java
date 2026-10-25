package id.neravoxa.erp.eam.ops;

import id.neravoxa.erp.core.ErpException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Padanan {@code multer({ limits: { fileSize }, fileFilter }).single(field)} di rute GA.
 *
 * <ul>
 *   <li>Request bukan multipart / tanpa berkas → null (sumber: {@code req.file} undefined → 400 di handler).</li>
 *   <li>MIME di luar daftar → berkas diabaikan diam-diam (fileFilter {@code cb(null, false)}) → null.</li>
 *   <li>Field berkas lain atau lebih dari satu berkas → MulterError LIMIT_UNEXPECTED_FILE; berkas melebihi
 *       batas → LIMIT_FILE_SIZE. Keduanya jatuh ke handler galat global Express → 500 generik.</li>
 * </ul>
 */
final class GaUploads {

    static final String SERVER_ERROR = "Terjadi kesalahan pada server. Silakan coba lagi.";

    private GaUploads() {
    }

    static MultipartFile single(HttpServletRequest request, String field, List<String> allowedMime, long maxBytes) {
        if (!(request instanceof MultipartHttpServletRequest mp)) {
            return null;
        }
        MultipartFile chosen = null;
        for (Map.Entry<String, List<MultipartFile>> e : mp.getMultiFileMap().entrySet()) {
            List<MultipartFile> files = e.getValue();
            if (files == null || files.isEmpty()) {
                continue;
            }
            if (!field.equals(e.getKey()) || files.size() > 1) {
                throw ErpException.of(500, SERVER_ERROR);
            }
            chosen = files.get(0);
        }
        if (chosen == null || !allowedMime.contains(chosen.getContentType())) {
            return null;
        }
        if (chosen.getSize() > maxBytes) {
            throw ErpException.of(500, SERVER_ERROR);
        }
        return chosen;
    }

    static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ErpException.of(500, SERVER_ERROR);
        }
    }
}
