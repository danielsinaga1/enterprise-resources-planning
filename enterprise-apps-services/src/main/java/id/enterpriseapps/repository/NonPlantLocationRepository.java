package id.enterpriseapps.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.NonPlantLocationEntity;

public interface NonPlantLocationRepository extends JpaRepository<NonPlantLocationEntity, Long> {
    List<NonPlantLocationEntity> findByDeletedAtIsNullOrderByNameAsc();
}
