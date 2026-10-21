package id.enterpriseapps.lims.repository;

import id.enterpriseapps.lims.entity.LimsStandard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LimsStandardRepository extends JpaRepository<LimsStandard, Long> {
    List<LimsStandard> findAllByDeletedAtIsNull();
    List<LimsStandard> findByMethodIdAndDeletedAtIsNull(Long methodId);
}

