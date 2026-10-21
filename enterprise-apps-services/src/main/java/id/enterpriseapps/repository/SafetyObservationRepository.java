package id.enterpriseapps.repository;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import id.enterpriseapps.entity.SafetyObservationEntity;

public interface SafetyObservationRepository extends JpaRepository<SafetyObservationEntity, Long> {

    Page<SafetyObservationEntity> findByDeletedAtIsNull(Pageable pageable);

    List<SafetyObservationEntity> findByDeletedAtIsNull();

    Page<SafetyObservationEntity> findByIsJsoAndDeletedAtIsNull(Boolean isJso, Pageable pageable);

    /**
     * Satu query untuk daftar observasi: filter tipe, tahun, dan pencarian sekaligus.
     * Parameter null = filter tidak dipakai.
     *
     * Tahun diambil dari COALESCE(date_event_jso, date_event), BUKAN date_event saja.
     * JSO menyimpan tanggalnya di date_event_jso; memakai date_event membuang setiap
     * baris JSO yang kolom NCR-nya kosong — di data dev itu 31 dari 193 observasi
     * hilang total dari filter tahun berapa pun.
     *
     * Pencarian dicocokkan di database, bukan di layar: memfilter halaman yang sedang
     * tampil saja berarti nomor laporan yang ada di halaman 5 terlihat "tidak ada".
     * Nilai :search harus sudah lowercase dan dibungkus % oleh pemanggil.
     */
    @Query("SELECT s FROM SafetyObservationEntity s WHERE s.deletedAt IS NULL "
            + "AND (:isJso IS NULL OR s.isJso = :isJso) "
            + "AND (:status IS NULL OR s.status = :status) "
            + "AND (:year IS NULL OR YEAR(COALESCE(s.observationDateJso, s.observationDate)) = :year) "
            + "AND (:search IS NULL OR LOWER(s.reportNumber) LIKE :search OR LOWER(s.title) LIKE :search)")
    Page<SafetyObservationEntity> searchObservations(@Param("isJso") Boolean isJso,
                                                     @Param("status") String status,
                                                     @Param("year") Integer year,
                                                     @Param("search") String search,
                                                     Pageable pageable);

    @Query("SELECT s FROM SafetyObservationEntity s WHERE s.deletedAt IS NULL AND s.reporterId = :reporterId")
    Page<SafetyObservationEntity> findByReporterIdAndDeletedAtIsNull(@Param("reporterId") Long reporterId, Pageable pageable);

    @Query("SELECT s FROM SafetyObservationEntity s WHERE s.deletedAt IS NULL AND s.status = :status")
    Page<SafetyObservationEntity> findByStatusAndDeletedAtIsNull(@Param("status") String status, Pageable pageable);

    long countByDeletedAtIsNull();

    long countByStatusAndDeletedAtIsNull(String status);

    long countByIsJsoAndDeletedAtIsNull(Boolean isJso);

    /**
     * Highest running sequence for a given report-number prefix (e.g. "SO-2506-").
     * Counts ALL rows (including soft-deleted) to avoid reusing a number that would
     * violate the unique constraint on no_laporan. prefixLen = length of the prefix
     * up to and including the trailing dash; the increment is the substring after it.
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(no_laporan, :prefixLen + 1) AS BIGINT)), 0) "
            + "FROM irms_safety_observations WHERE no_laporan LIKE :prefixLike", nativeQuery = true)
    long findMaxReportSequence(@Param("prefixLen") int prefixLen, @Param("prefixLike") String prefixLike);

    @Query(value = "SELECT d.name AS department, s.status AS status, COUNT(*) AS count FROM irms_safety_observations s JOIN irms_origin_departments d ON s.dept_origin_id = d.id WHERE s.deleted_at IS NULL AND s.dept_origin_id IS NOT NULL GROUP BY d.name, s.status", nativeQuery = true)
    List<Map<String, Object>> countByDepartmentAndStatus();

    @Query(value = "SELECT to_char(s.created_at, 'Mon YYYY') AS label, COUNT(*) AS value FROM irms_safety_observations s WHERE s.deleted_at IS NULL AND s.created_at >= NOW() - INTERVAL '12 months' GROUP BY to_char(s.created_at, 'YYYY-MM'), to_char(s.created_at, 'Mon YYYY') ORDER BY to_char(s.created_at, 'YYYY-MM')", nativeQuery = true)
    List<Map<String, Object>> countByMonthRecent();

    @Query(value = "SELECT CASE WHEN s.root_cause_id IS NOT NULL THEN 'has_root_cause' ELSE 'no_root_cause' END AS label, COUNT(*) AS value FROM irms_safety_observations s WHERE s.deleted_at IS NULL GROUP BY CASE WHEN s.root_cause_id IS NOT NULL THEN 'has_root_cause' ELSE 'no_root_cause' END", nativeQuery = true)
    List<Map<String, Object>> countByRootCausePresence();
}

