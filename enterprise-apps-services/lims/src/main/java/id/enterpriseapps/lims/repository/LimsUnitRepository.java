package id.enterpriseapps.lims.repository;

import id.enterpriseapps.lims.entity.LimsUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LimsUnitRepository extends JpaRepository<LimsUnit, Long> {
    List<LimsUnit> findAllByDeletedAtIsNull();
}

