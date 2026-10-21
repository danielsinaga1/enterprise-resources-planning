package id.enterpriseapps.lims.repository;

import id.enterpriseapps.lims.entity.LimsMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LimsMethodRepository extends JpaRepository<LimsMethod, Long> {
    List<LimsMethod> findAllByDeletedAtIsNull();
}

