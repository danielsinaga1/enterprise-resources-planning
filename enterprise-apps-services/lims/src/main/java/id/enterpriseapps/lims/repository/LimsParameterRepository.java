package id.enterpriseapps.lims.repository;

import id.enterpriseapps.lims.entity.LimsParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LimsParameterRepository extends JpaRepository<LimsParameter, Long> {
    List<LimsParameter> findAllByDeletedAtIsNull();
}

