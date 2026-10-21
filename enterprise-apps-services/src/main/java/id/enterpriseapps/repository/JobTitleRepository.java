package id.enterpriseapps.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.JobTitleEntity;

public interface JobTitleRepository extends JpaRepository<JobTitleEntity, Long> {
    Optional<JobTitleEntity> findFirstByNameIgnoreCase(String name);
}

