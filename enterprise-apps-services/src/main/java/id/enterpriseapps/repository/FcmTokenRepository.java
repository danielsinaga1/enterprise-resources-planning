package id.enterpriseapps.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import id.enterpriseapps.entity.FcmTokenEntity;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmTokenEntity, Long> {

    /** Find all tokens for a given user (supports multiple devices) */
    List<FcmTokenEntity> findByUserId(Long userId);

    /** Find a specific token string */
    Optional<FcmTokenEntity> findByToken(String token);

    /** Check if token exists */
    boolean existsByToken(String token);

    /** Delete by token string (used on logout / unregister) */
    void deleteByToken(String token);

    /** Find tokens for multiple users (batch send) */
    List<FcmTokenEntity> findByUserIdIn(List<Long> userIds);
}

