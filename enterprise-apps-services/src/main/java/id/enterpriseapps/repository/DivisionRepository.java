package id.enterpriseapps.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.DivisionEntity;

public interface DivisionRepository extends JpaRepository<DivisionEntity, Long> {
    List<DivisionEntity> findByDeletedAtIsNull();
}

