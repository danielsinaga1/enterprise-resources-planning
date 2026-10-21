package id.enterpriseapps.lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Permintaan penolakan laporan. Hanya memuat alasan — status tujuan ditentukan
 * backend supaya workflow tidak bisa dilompati lewat payload.
 */
public record LimsRejectRequest(
        @NotBlank(message = "Alasan penolakan wajib diisi")
        @Size(max = 2000, message = "Alasan penolakan maksimal 2000 karakter")
        String reason
) {
}
