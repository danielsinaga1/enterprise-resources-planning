package id.enterpriseapps.repository;

import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import id.enterpriseapps.entity.JsoReportEntity;

public interface JsoReportRepository extends JpaRepository<JsoReportEntity, Long> {
    List<JsoReportEntity> findBySoIdAndDeletedAtIsNull(Long soId);

    /* ============================================================
       AGREGASI DASHBOARD TEMUAN
       ------------------------------------------------------------
       Tanggal temuan diambil berjenjang: date_event milik temuan dulu,
       baru tanggal observasi induknya. Kolom date_event di temuan banyak
       yang kosong pada data impor, dan tanggal JSO induk sendiri tersimpan
       di date_event_jso — memakai satu kolom saja membuang sebagian besar
       baris dari hitungan per bulan.

       Sebuah temuan dihitung "Close" bila sudah ada tindak lanjutnya
       (corrective terisi) atau observasi induknya sudah ditutup. Status
       'Close' berkapital ikut dicocokkan karena data impor lama memakainya.
       ============================================================ */

    /** Jumlah temuan dan berapa yang sudah tertutup pada satu tahun (null = semua tahun). */
    @Query(value = """
            SELECT COUNT(*) AS total,
                   COUNT(*) FILTER (
                       WHERE (j.corrective IS NOT NULL AND j.corrective <> '')
                          OR LOWER(so.status) IN ('closed', 'close')
                   ) AS closed
            FROM irms_jso_reports j
            JOIN irms_safety_observations so ON so.id = j.so_id
            WHERE j.deleted_at IS NULL AND so.deleted_at IS NULL
              AND (CAST(:year AS INTEGER) IS NULL
                   OR EXTRACT(YEAR FROM COALESCE(j.date_event, so.date_event_jso, so.date_event)) = :year)
            """, nativeQuery = true)
    Map<String, Object> findingStats(@Param("year") Integer year);

    /** Sebaran temuan per kategori (K3, PS, LH, ...) pada satu tahun. */
    @Query(value = """
            SELECT COALESCE(rc.name, 'Tanpa Kategori') AS label, COUNT(*) AS value
            FROM irms_jso_reports j
            JOIN irms_safety_observations so ON so.id = j.so_id
            LEFT JOIN irms_root_causes rc ON rc.id = j.category_id
            WHERE j.deleted_at IS NULL AND so.deleted_at IS NULL
              AND (CAST(:year AS INTEGER) IS NULL
                   OR EXTRACT(YEAR FROM COALESCE(j.date_event, so.date_event_jso, so.date_event)) = :year)
            GROUP BY COALESCE(rc.name, 'Tanpa Kategori')
            ORDER BY COUNT(*) DESC
            """, nativeQuery = true)
    List<Map<String, Object>> findingsByCategory(@Param("year") Integer year);

    /**
     * Jumlah temuan per (tahun, bulan) sejak tahun tertentu — sumber grafik garis
     * yang membandingkan beberapa tahun sekaligus.
     */
    @Query(value = """
            SELECT EXTRACT(YEAR FROM COALESCE(j.date_event, so.date_event_jso, so.date_event)) AS yr,
                   EXTRACT(MONTH FROM COALESCE(j.date_event, so.date_event_jso, so.date_event)) AS mo,
                   COUNT(*) AS value
            FROM irms_jso_reports j
            JOIN irms_safety_observations so ON so.id = j.so_id
            WHERE j.deleted_at IS NULL AND so.deleted_at IS NULL
              AND COALESCE(j.date_event, so.date_event_jso, so.date_event) IS NOT NULL
              AND EXTRACT(YEAR FROM COALESCE(j.date_event, so.date_event_jso, so.date_event)) >= :fromYear
            GROUP BY yr, mo
            ORDER BY yr, mo
            """, nativeQuery = true)
    List<Map<String, Object>> findingsByYearMonth(@Param("fromYear") int fromYear);

    /** Total dan jumlah tertutup per bulan pada satu tahun — sumber grafik persentase tindak lanjut. */
    @Query(value = """
            SELECT EXTRACT(MONTH FROM COALESCE(j.date_event, so.date_event_jso, so.date_event)) AS mo,
                   COUNT(*) AS total,
                   COUNT(*) FILTER (
                       WHERE (j.corrective IS NOT NULL AND j.corrective <> '')
                          OR LOWER(so.status) IN ('closed', 'close')
                   ) AS closed
            FROM irms_jso_reports j
            JOIN irms_safety_observations so ON so.id = j.so_id
            WHERE j.deleted_at IS NULL AND so.deleted_at IS NULL
              AND COALESCE(j.date_event, so.date_event_jso, so.date_event) IS NOT NULL
              AND (CAST(:year AS INTEGER) IS NULL
                   OR EXTRACT(YEAR FROM COALESCE(j.date_event, so.date_event_jso, so.date_event)) = :year)
            GROUP BY mo
            ORDER BY mo
            """, nativeQuery = true)
    List<Map<String, Object>> findingsFollowUpByMonth(@Param("year") Integer year);

    /** Tahun-tahun yang punya temuan — untuk mengisi dropdown filter tahun. */
    @Query(value = """
            SELECT DISTINCT EXTRACT(YEAR FROM COALESCE(j.date_event, so.date_event_jso, so.date_event)) AS yr
            FROM irms_jso_reports j
            JOIN irms_safety_observations so ON so.id = j.so_id
            WHERE j.deleted_at IS NULL AND so.deleted_at IS NULL
              AND COALESCE(j.date_event, so.date_event_jso, so.date_event) IS NOT NULL
            ORDER BY yr DESC
            """, nativeQuery = true)
    List<Integer> findingYears();
}
