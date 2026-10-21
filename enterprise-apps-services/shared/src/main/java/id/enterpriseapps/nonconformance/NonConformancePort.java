package id.enterpriseapps.nonconformance;

import java.util.Optional;

/**
 * Port pembuatan laporan ketidaksesuaian (NCR) untuk modul fitur.
 *
 * <p>Hasil uji di luar spesifikasi tidak boleh berhenti sebagai angka merah di
 * layar lab — ia harus menjadi temuan yang ditindaklanjuti. Pencatatan temuan
 * dimiliki modul safety observation di core, sementara ArchUnit melarang
 * {@code :lims} mengimpor {@code id.enterpriseapps.service..} agar LIMS tetap
 * bisa diangkat jadi microservice. Interface ini jadi titik temunya, mengikuti
 * pola {@code id.enterpriseapps.mail.WorkflowMailPort}.
 *
 * <p>Implementasinya boleh tidak ada (mis. LIMS dijalankan tanpa modul safety).
 * Pemanggil memperlakukan ketiadaan itu sebagai fitur mati, bukan galat.
 */
public interface NonConformancePort {

    /**
     * Membuka NCR dari satu hasil uji yang di luar spesifikasi.
     *
     * @return referensi NCR yang terbentuk, atau {@link Optional#empty()} bila
     *         pembuatannya gagal — pemanggil tidak boleh menggagalkan pekerjaan
     *         lab karenanya.
     */
    Optional<NonConformanceRef> openFromLabResult(LabResultNonConformance request);
}
