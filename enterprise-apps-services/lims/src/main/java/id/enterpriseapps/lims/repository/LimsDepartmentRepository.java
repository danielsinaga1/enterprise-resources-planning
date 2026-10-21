package id.enterpriseapps.lims.repository;

import id.enterpriseapps.lims.entity.LimsDepartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LimsDepartmentRepository extends JpaRepository<LimsDepartment, Long> {
    List<LimsDepartment> findAllByDeletedAtIsNull();
}

