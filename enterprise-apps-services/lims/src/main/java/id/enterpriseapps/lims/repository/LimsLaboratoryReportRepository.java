package id.enterpriseapps.lims.repository;

import id.enterpriseapps.lims.entity.LimsLaboratoryReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface LimsLaboratoryReportRepository extends JpaRepository<LimsLaboratoryReport, Long> {

    Page<LimsLaboratoryReport> findAllByDeletedAtIsNull(Pageable pageable);

    /**
     * Server-side search by status and free-text. Pass null/empty to skip a filter.
     * Searches noReport, location, requestedBy, and department name (case-insensitive).
     */
    @Query("SELECT lr FROM LimsLaboratoryReport lr LEFT JOIN lr.department d "
            + "WHERE lr.deletedAt IS NULL "
            + "AND (:status IS NULL OR lr.status = :status) "
            + "AND (:search IS NULL OR :search = '' "
            + "     OR LOWER(lr.noReport) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "     OR LOWER(lr.location) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "     OR LOWER(lr.requestedBy) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "     OR LOWER(d.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<LimsLaboratoryReport> searchByStatusAndText(@Param("status") String status,
                                                    @Param("search") String search,
                                                    Pageable pageable);

    Optional<LimsLaboratoryReport> findByIdAndDeletedAtIsNull(Long id);

    List<LimsLaboratoryReport> findByStatusAndDeletedAtIsNull(String status);

    List<LimsLaboratoryReport> findByDepartmentIdAndDeletedAtIsNull(Long departmentId);

    @Query("SELECT lr FROM LimsLaboratoryReport lr WHERE lr.deletedAt IS NULL AND lr.reporterId = :reporterId")
    List<LimsLaboratoryReport> findByReporterId(@Param("reporterId") Long reporterId);

    @Query("SELECT COUNT(lr) FROM LimsLaboratoryReport lr WHERE lr.deletedAt IS NULL AND YEAR(lr.createdAt) = :year AND MONTH(lr.createdAt) = :month")
    long countByYearMonth(@Param("year") int year, @Param("month") int month);

    long countByDeletedAtIsNull();

    long countByStatusAndDeletedAtIsNull(String status);

    @Query("SELECT d.name AS label, COUNT(lr) AS value FROM LimsLaboratoryReport lr " +
           "JOIN lr.department d WHERE lr.deletedAt IS NULL GROUP BY d.name")
    List<Map<String, Object>> countByDepartment();

    @Query("SELECT lr.status AS label, COUNT(lr) AS value FROM LimsLaboratoryReport lr " +
           "WHERE lr.deletedAt IS NULL GROUP BY lr.status")
    List<Map<String, Object>> countGroupByStatus();

    // Returns the month NUMBER (1..12), mapped to a month name in the service. MONTHNAME() is a
    // MySQL-only function — Hibernate can't translate it on the PostgreSQL dialect, and it made
    // the whole LIMS dashboard endpoint 500. MONTH() is a portable JPQL function.
    @Query("SELECT MONTH(lr.createdAt) AS label, COUNT(lr) AS value FROM LimsLaboratoryReport lr " +
           "WHERE lr.deletedAt IS NULL AND YEAR(lr.createdAt) = :year GROUP BY MONTH(lr.createdAt) " +
           "ORDER BY MONTH(lr.createdAt)")
    List<Map<String, Object>> countByMonthInYear(@Param("year") int year);
}

