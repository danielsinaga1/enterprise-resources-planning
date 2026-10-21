package id.enterpriseapps.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.UserUserAlertEntity;

public interface UserUserAlertRepository extends JpaRepository<UserUserAlertEntity, Long> {
    List<UserUserAlertEntity> findByUserIdOrderByIdDesc(Long userId);

    long countByUserIdAndIsRead(Long userId, Boolean isRead);

    Optional<UserUserAlertEntity> findByUserAlertIdAndUserId(Long userAlertId, Long userId);

    List<UserUserAlertEntity> findByUserIdAndIsRead(Long userId, Boolean isRead);
}

