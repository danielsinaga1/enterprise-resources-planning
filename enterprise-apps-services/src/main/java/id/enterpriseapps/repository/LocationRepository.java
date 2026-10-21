package id.enterpriseapps.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.LocationEntity;

public interface LocationRepository extends JpaRepository<LocationEntity, Long> {

    List<LocationEntity> findByRegionNcrIdAndDeletedAtIsNull(Long regionNcrId);

    List<LocationEntity> findByDeletedAtIsNull();
}

