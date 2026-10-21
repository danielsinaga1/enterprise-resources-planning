package id.enterpriseapps.lims.repository;

import id.enterpriseapps.lims.entity.LimsChemicalCompound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LimsChemicalCompoundRepository extends JpaRepository<LimsChemicalCompound, Long> {

    List<LimsChemicalCompound> findAllByDeletedAtIsNull();

    List<LimsChemicalCompound> findByCategoryAndDeletedAtIsNull(String category);

    List<LimsChemicalCompound> findByIsActiveAndDeletedAtIsNull(Boolean isActive);

    List<LimsChemicalCompound> findByNameContainingIgnoreCaseAndDeletedAtIsNull(String name);
}

