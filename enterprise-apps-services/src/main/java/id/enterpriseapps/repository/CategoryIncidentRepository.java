package id.enterpriseapps.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.CategoryIncidentEntity;

public interface CategoryIncidentRepository extends JpaRepository<CategoryIncidentEntity, Long> {
}

