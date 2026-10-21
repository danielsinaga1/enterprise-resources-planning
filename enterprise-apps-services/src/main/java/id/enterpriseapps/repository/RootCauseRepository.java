package id.enterpriseapps.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.RootCauseEntity;

public interface RootCauseRepository extends JpaRepository<RootCauseEntity, Long> {
}

