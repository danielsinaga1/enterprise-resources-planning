package id.enterpriseapps.lims.repository;

import id.enterpriseapps.lims.entity.LimsResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LimsResultRepository extends JpaRepository<LimsResult, Long> {
    List<LimsResult> findAllByDeletedAtIsNull();
}

