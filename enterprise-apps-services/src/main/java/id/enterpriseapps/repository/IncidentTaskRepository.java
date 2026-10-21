package id.enterpriseapps.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import id.enterpriseapps.entity.IncidentTaskEntity;

public interface IncidentTaskRepository extends JpaRepository<IncidentTaskEntity, Long> {

    List<IncidentTaskEntity> findByIncidentReportIdAndDeletedAtIsNull(Long incidentReportId);

    Page<IncidentTaskEntity> findByAssignerIdAndDeletedAtIsNull(Long assignerId, Pageable pageable);

    Page<IncidentTaskEntity> findByAssigneeIdAndDeletedAtIsNull(Long assigneeId, Pageable pageable);

    Page<IncidentTaskEntity> findByAssignerIdAndStatusAndDeletedAtIsNull(Long assignerId, String status, Pageable pageable);

    Page<IncidentTaskEntity> findByAssigneeIdAndStatusAndDeletedAtIsNull(Long assigneeId, String status, Pageable pageable);

    Page<IncidentTaskEntity> findByDeletedAtIsNull(Pageable pageable);

    Page<IncidentTaskEntity> findByStatusAndDeletedAtIsNull(String status, Pageable pageable);

    long countByAssignerIdAndStatusAndDeletedAtIsNull(Long assignerId, String status);

    long countByAssigneeIdAndStatusAndDeletedAtIsNull(Long assigneeId, String status);

    long countByAssignerIdAndDeletedAtIsNull(Long assignerId);

    long countByAssigneeIdAndDeletedAtIsNull(Long assigneeId);

    @Query("SELECT t FROM IncidentTaskEntity t WHERE t.assignerId = :assignerId " +
           "AND t.deadline < :date AND t.status <> :excludeStatus AND t.deletedAt IS NULL")
    List<IncidentTaskEntity> findOverdueTasks(@Param("assignerId") Long assignerId,
                                              @Param("date") LocalDate date,
                                              @Param("excludeStatus") String excludeStatus);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(t.taskNumber, LENGTH(:prefix) + 1) AS int)), 0) " +
           "FROM IncidentTaskEntity t WHERE t.taskNumber LIKE :prefix")
    long findMaxTaskSeq(@Param("prefix") String prefix);
}

