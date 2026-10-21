package id.enterpriseapps.repository;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import id.enterpriseapps.entity.IncidentReportEntity;

public interface IncidentReportRepository extends JpaRepository<IncidentReportEntity, Long> {

    Page<IncidentReportEntity> findByDeletedAtIsNull(Pageable pageable);

    List<IncidentReportEntity> findByDeletedAtIsNull();

    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND i.reporterId = :reporterId")
    Page<IncidentReportEntity> findByReporterIdAndDeletedAtIsNull(@Param("reporterId") Long reporterId, Pageable pageable);

    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND i.status = :status")
    Page<IncidentReportEntity> findByStatusAndDeletedAtIsNull(@Param("status") String status, Pageable pageable);

    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND i.status IN :statuses")
    Page<IncidentReportEntity> findByStatusInAndDeletedAtIsNull(@Param("statuses") List<String> statuses, Pageable pageable);

    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND i.designatedManagerId = :managerId AND i.status = :status")
    Page<IncidentReportEntity> findByDesignatedManagerIdAndStatusAndDeletedAtIsNull(
            @Param("managerId") Long managerId, @Param("status") String status, Pageable pageable);

    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND i.designatedManagerId = :managerId")
    Page<IncidentReportEntity> findByDesignatedManagerIdAndDeletedAtIsNull(
            @Param("managerId") Long managerId, Pageable pageable);

    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND i.assignedToId = :userId AND i.status = :status")
    Page<IncidentReportEntity> findByAssignedToIdAndStatusAndDeletedAtIsNull(
            @Param("userId") Long userId, @Param("status") String status, Pageable pageable);

    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND i.actionById = :actionById")
    Page<IncidentReportEntity> findByActionByIdAndDeletedAtIsNull(@Param("actionById") Long actionById, Pageable pageable);

    long countByDeletedAtIsNull();

    long countByStatusAndDeletedAtIsNull(String status);

    @Query(value = "SELECT c.name AS label, COUNT(*) AS value FROM irms_incident_reports i JOIN irms_category_incidents c ON i.category_id = c.id WHERE i.deleted_at IS NULL AND i.category_id IS NOT NULL GROUP BY c.name", nativeQuery = true)
    List<Map<String, Object>> countByCategory();

    @Query(value = "SELECT d.name AS label, COUNT(*) AS value FROM irms_incident_reports i JOIN irms_origin_departments d ON i.dept_origin_id = d.id WHERE i.deleted_at IS NULL AND i.dept_origin_id IS NOT NULL GROUP BY d.name", nativeQuery = true)
    List<Map<String, Object>> countByOriginDepartment();

    @Query(value = "SELECT d.name AS label, COUNT(*) AS value FROM irms_incident_reports i JOIN irms_designation_departments d ON i.dept_designated_id = d.id WHERE i.deleted_at IS NULL AND i.dept_designated_id IS NOT NULL GROUP BY d.name", nativeQuery = true)
    List<Map<String, Object>> countByDesignationDepartment();

    @Query(value = "SELECT cl.name AS classification, c.name AS category, COUNT(*) AS count FROM irms_incident_reports i JOIN irms_classification_incidents cl ON i.classify_id = cl.id JOIN irms_category_incidents c ON i.category_id = c.id WHERE i.deleted_at IS NULL AND i.classify_id IS NOT NULL AND i.category_id IS NOT NULL GROUP BY cl.name, c.name", nativeQuery = true)
    List<Map<String, Object>> getRiskMatrix();

    @Query(value = "SELECT to_char(i.date_incident, 'Mon YYYY') AS label, COUNT(*) AS value "
            + "FROM irms_incident_reports i WHERE i.deleted_at IS NULL AND i.date_incident IS NOT NULL "
            + "AND i.date_incident >= CURRENT_DATE - INTERVAL '12 months' "
            + "GROUP BY to_char(i.date_incident, 'YYYY-MM'), to_char(i.date_incident, 'Mon YYYY') "
            + "ORDER BY to_char(i.date_incident, 'YYYY-MM')", nativeQuery = true)
    List<Map<String, Object>> countByMonthRecent();

    @Query(value = "SELECT i.status AS label, COUNT(*) AS value FROM irms_incident_reports i WHERE i.deleted_at IS NULL GROUP BY i.status", nativeQuery = true)
    List<Map<String, Object>> countGroupByStatus();

    @Query("SELECT MAX(i.reportNumber) FROM IncidentReportEntity i WHERE i.reportNumber LIKE :prefix")
    String findMaxReportNumberByPrefix(@Param("prefix") String prefix);

    /**
     * Numeric MAX of the trailing sequence in report numbers like "IR-YYYYMM-NNNNN".
     * Avoids the lexicographic bug where "10000" < "9999" as strings.
     * Uses native PostgreSQL SUBSTRING + CAST.
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(no_laporan, :prefixLen + 1) AS BIGINT)), 0) "
            + "FROM irms_incident_reports WHERE no_laporan LIKE :prefixLike", nativeQuery = true)
    Long findMaxSequenceByPrefix(@Param("prefixLike") String prefixLike, @Param("prefixLen") int prefixLen);

    // Task list: filter by designation department ID + status (for department-based task assignment)
    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND i.designationDepartment.id = :deptId AND i.status IN :statuses")
    Page<IncidentReportEntity> findByDesignationDepartmentIdAndStatusInAndDeletedAtIsNull(
            @Param("deptId") Long deptId, @Param("statuses") List<String> statuses, Pageable pageable);

    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND i.designationDepartment.id = :deptId AND i.status = :status")
    Page<IncidentReportEntity> findByDesignationDepartmentIdAndStatusAndDeletedAtIsNull(
            @Param("deptId") Long deptId, @Param("status") String status, Pageable pageable);

    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND i.designationDepartment.id = :deptId")
    Page<IncidentReportEntity> findByDesignationDepartmentIdAndDeletedAtIsNull(
            @Param("deptId") Long deptId, Pageable pageable);

    // Year filter queries
    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND YEAR(i.incidentDate) = :year")
    Page<IncidentReportEntity> findByYearAndDeletedAtIsNull(@Param("year") int year, Pageable pageable);

    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND i.status = :status AND YEAR(i.incidentDate) = :year")
    Page<IncidentReportEntity> findByStatusAndYearAndDeletedAtIsNull(@Param("status") String status, @Param("year") int year, Pageable pageable);

    @Query(value = "SELECT DISTINCT CAST(EXTRACT(YEAR FROM date_incident) AS INTEGER) AS y FROM irms_incident_reports WHERE deleted_at IS NULL AND date_incident IS NOT NULL ORDER BY y DESC", nativeQuery = true)
    List<Integer> findDistinctYears();

    // Search by report number or title (with optional status and year filters)
    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND (LOWER(i.reportNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<IncidentReportEntity> searchByReportNumberOrTitle(@Param("search") String search, Pageable pageable);

    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND i.status = :status AND (LOWER(i.reportNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<IncidentReportEntity> searchByReportNumberOrTitleAndStatus(@Param("search") String search, @Param("status") String status, Pageable pageable);

    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND YEAR(i.incidentDate) = :year AND (LOWER(i.reportNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<IncidentReportEntity> searchByReportNumberOrTitleAndYear(@Param("search") String search, @Param("year") int year, Pageable pageable);

    @Query("SELECT i FROM IncidentReportEntity i WHERE i.deletedAt IS NULL AND i.status = :status AND YEAR(i.incidentDate) = :year AND (LOWER(i.reportNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<IncidentReportEntity> searchByReportNumberOrTitleAndStatusAndYear(@Param("search") String search, @Param("status") String status, @Param("year") int year, Pageable pageable);
}

