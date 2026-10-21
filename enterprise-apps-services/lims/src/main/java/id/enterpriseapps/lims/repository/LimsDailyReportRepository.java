package id.enterpriseapps.lims.repository;

import id.enterpriseapps.lims.entity.LimsDailyReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LimsDailyReportRepository extends JpaRepository<LimsDailyReport, Long> {
    Page<LimsDailyReport> findAllByDeletedAtIsNull(Pageable pageable);
    List<LimsDailyReport> findByDueDateBetweenAndDeletedAtIsNull(LocalDate start, LocalDate end);
    List<LimsDailyReport> findByAssignedToIdAndDeletedAtIsNull(Long assignedToId);

    long countByDeletedAtIsNull();
}

