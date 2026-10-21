package id.enterpriseapps.repository;

import id.enterpriseapps.entity.ObservationTaskEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ObservationTaskRepository extends JpaRepository<ObservationTaskEntity, Long> {

    Page<ObservationTaskEntity> findByDeletedAtIsNull(Pageable pageable);

    @Query("SELECT t FROM ObservationTaskEntity t WHERE t.safetyObservation.id = :soId AND t.deletedAt IS NULL ORDER BY t.createdAt DESC")
    List<ObservationTaskEntity> findBySafetyObservationId(@Param("soId") Long soId);

    @Query("SELECT t FROM ObservationTaskEntity t WHERE t.assignedToId = :userId AND t.deletedAt IS NULL ORDER BY t.createdAt DESC")
    List<ObservationTaskEntity> findByAssignedToId(@Param("userId") Long userId);

    @Query("SELECT t FROM ObservationTaskEntity t WHERE t.status = :status AND t.deletedAt IS NULL")
    Page<ObservationTaskEntity> findByStatusAndDeletedAtIsNull(@Param("status") String status, Pageable pageable);

    long countByDeletedAtIsNull();

    long countByStatusAndDeletedAtIsNull(String status);

    @Query("SELECT t.status, COUNT(t) FROM ObservationTaskEntity t WHERE t.deletedAt IS NULL GROUP BY t.status")
    List<Object[]> countByStatus();

    @Query("SELECT t FROM ObservationTaskEntity t WHERE t.assignedToId = :userId AND t.status IN ('open', 'in_progress') AND t.deletedAt IS NULL ORDER BY t.dueDate ASC")
    List<ObservationTaskEntity> findActiveTasksByUserId(@Param("userId") Long userId);

    /**
     * Tugas JSO yang tenggatnya jatuh di dalam rentang tertentu — dipakai pengingat
     * H-3 / Hari-H / H+3. Rentang (bukan kesamaan tanggal) dipakai karena due_date
     * bertipe timestamp: tugas bertenggat pukul 17:00 tetap harus ikut terjaring.
     * Observasi NCR sengaja dikecualikan lewat is_jso.
     */
    @Query("SELECT t FROM ObservationTaskEntity t WHERE t.deletedAt IS NULL "
            + "AND t.dueDate BETWEEN :start AND :end "
            + "AND t.status IN :statuses "
            + "AND t.safetyObservation.isJso = true "
            + "ORDER BY t.dueDate ASC")
    List<ObservationTaskEntity> findJsoTasksDueBetween(@Param("start") LocalDateTime start,
                                                       @Param("end") LocalDateTime end,
                                                       @Param("statuses") List<String> statuses);

    /**
     * Tugas yang tenggatnya sudah lewat tapi statusnya masih menuntut tindakan.
     *
     * <p>Sengaja TIDAK disaring `is_jso`: status `overdue` sebelumnya tidak pernah
     * dituliskan oleh kode mana pun, hanya dihitung di getTaskStats() dan difilter
     * di UI — akibatnya kartu "Overdue" selalu 0 untuk JSO maupun NCR. Menyaring
     * JSO saja hanya akan memperbaiki separuh kartu itu.</p>
     */
    @Query("SELECT t FROM ObservationTaskEntity t WHERE t.deletedAt IS NULL "
            + "AND t.dueDate IS NOT NULL AND t.dueDate < :batas "
            + "AND t.status IN ('open', 'in_progress')")
    List<ObservationTaskEntity> findTasksPastDue(@Param("batas") LocalDateTime batas);
}

