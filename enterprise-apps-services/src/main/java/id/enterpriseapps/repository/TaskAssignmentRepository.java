package id.enterpriseapps.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import id.enterpriseapps.entity.TaskAssignmentEntity;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignmentEntity, Long> {

    Page<TaskAssignmentEntity> findByDeletedAtIsNull(Pageable pageable);

    @Query("SELECT t FROM TaskAssignmentEntity t LEFT JOIN FETCH t.assignee WHERE t.deletedAt IS NULL " +
           "AND t.startDate <= :endDate AND t.endDate >= :startDate")
    List<TaskAssignmentEntity> findByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT t FROM TaskAssignmentEntity t LEFT JOIN FETCH t.assignee WHERE t.deletedAt IS NULL " +
           "AND t.assigneeId = :assigneeId " +
           "AND t.startDate <= :endDate AND t.endDate >= :startDate")
    List<TaskAssignmentEntity> findByAssigneeAndDateRange(
            @Param("assigneeId") Long assigneeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT t FROM TaskAssignmentEntity t WHERE t.deletedAt IS NULL AND t.status = :status")
    Page<TaskAssignmentEntity> findByStatusAndDeletedAtIsNull(
            @Param("status") String status, Pageable pageable);

    @Query("SELECT t FROM TaskAssignmentEntity t WHERE t.deletedAt IS NULL AND t.assigneeId = :assigneeId")
    Page<TaskAssignmentEntity> findByAssigneeIdAndDeletedAtIsNull(
            @Param("assigneeId") Long assigneeId, Pageable pageable);

    long countByDeletedAtIsNull();

    long countByStatusAndDeletedAtIsNull(String status);
}

