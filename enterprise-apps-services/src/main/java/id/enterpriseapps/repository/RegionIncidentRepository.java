package id.enterpriseapps.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.RegionIncidentEntity;

public interface RegionIncidentRepository extends JpaRepository<RegionIncidentEntity, Long> {
}

