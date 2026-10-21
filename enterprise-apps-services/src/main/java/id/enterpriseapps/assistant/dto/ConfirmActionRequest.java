package id.enterpriseapps.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * Payload saat pengguna menyetujui aksi tulis dari kartu konfirmasi KPIntar.
 * Server memvalidasi ulang bahwa {@code tool} memang tool tulis yang terdaftar sebelum
 * mengeksekusinya atas nama pengguna yang sedang login.
 */
@Data
public class ConfirmActionRequest {
    @NotBlank(message = "Nama tool wajib diisi")
    private String tool;
    /** Argumen yang tadi ditampilkan di kartu konfirmasi. */
    private Map<String, Object> arguments;
}
