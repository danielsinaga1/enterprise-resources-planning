package id.enterpriseapps.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import id.enterpriseapps.entity.JsoReminderLogEntity;

@Repository
public interface JsoReminderLogRepository extends JpaRepository<JsoReminderLogEntity, Long> {

    /**
     * Sudah pernah dikirim untuk kombinasi tugas + tahap + tenggat ini?
     *
     * <p>Tenggat ikut jadi kunci supaya perpanjangan tenggat memicu pengingat baru:
     * tanpa itu, tugas yang tenggatnya digeser tidak akan pernah diingatkan lagi.</p>
     */
    boolean existsByTaskIdAndStageAndDueOn(Long taskId, String stage, LocalDate dueOn);
}
