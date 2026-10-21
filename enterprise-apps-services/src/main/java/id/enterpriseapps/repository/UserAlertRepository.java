package id.enterpriseapps.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.UserAlertEntity;

public interface UserAlertRepository extends JpaRepository<UserAlertEntity, Long> {
    List<UserAlertEntity> findByIdInOrderByCreatedAtDesc(List<Long> ids);
}

