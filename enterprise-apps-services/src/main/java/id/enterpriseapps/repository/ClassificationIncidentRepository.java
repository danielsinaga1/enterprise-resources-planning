package id.enterpriseapps.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.ClassificationIncidentEntity;

public interface ClassificationIncidentRepository extends JpaRepository<ClassificationIncidentEntity, Long> {
}

