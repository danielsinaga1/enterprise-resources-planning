package id.enterpriseapps.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.ResultEntity;

public interface ResultRepository extends JpaRepository<ResultEntity, Long> {
    Optional<ResultEntity> findByName(String name);
}

