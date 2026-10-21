package id.enterpriseapps.lims.repository;

import id.enterpriseapps.lims.entity.LimsTestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LimsTestTypeRepository extends JpaRepository<LimsTestType, Long> {
    List<LimsTestType> findAllByDeletedAtIsNull();
    List<LimsTestType> findByParameterIdAndDeletedAtIsNull(Long parameterId);
}

