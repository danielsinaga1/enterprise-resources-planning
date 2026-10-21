package id.enterpriseapps.smartdocs.repository;

import id.enterpriseapps.smartdocs.entity.SmartDocsActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmartDocsActivityLogRepository extends JpaRepository<SmartDocsActivityLog, Integer> {

    Page<SmartDocsActivityLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
