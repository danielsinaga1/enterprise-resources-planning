package id.enterpriseapps.nonconformance;

import java.time.LocalDate;

import lombok.Builder;

/**
 * Satu hasil uji di luar spesifikasi, dalam bentuk yang bisa dibaca modul mana
 * pun tanpa mengenal tabel LIMS.
 *
 * <p>Lokasi dan departemen dikirim sebagai <b>teks</b>, bukan id: master lokasi
 * dan departemen LIMS berbeda tabel dengan master milik modul safety, sehingga
 * memetakan id antar keduanya akan menghasilkan rujukan yang salah tanpa
 * terlihat. Yang id-nya memang satu makna hanyalah {@code reporterId} — snapshot
 * {@code lims_users} memakai id pengguna yang sama dengan auth.
 */
@Builder
public record LabResultNonConformance(
        Long labReportId,
        String labReportNumber,
        Long analysisDetailId,
        String parameterName,
        String methodName,
        /** Hasil apa adanya seperti ditulis analis, mis. "20,5". */
        String resultValue,
        String unitName,
        /** Ringkasan batas yang dilanggar, mis. "maks. 15". */
        String specLimit,
        String location,
        String departmentName,
        String analystName,
        /** Pengguna yang dicatat sebagai pelapor temuan (id auth). */
        Long reporterId,
        LocalDate analysisDate
) {
}
