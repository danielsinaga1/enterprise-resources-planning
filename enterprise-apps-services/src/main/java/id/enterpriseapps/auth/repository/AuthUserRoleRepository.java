package id.enterpriseapps.auth.repository;

import id.enterpriseapps.auth.entity.AuthUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthUserRoleRepository extends JpaRepository<AuthUserRole, Long> {

    List<AuthUserRole> findByUserId(Long userId);

    List<AuthUserRole> findByUserIdAndIsActiveTrue(Long userId);

    List<AuthUserRole> findByRoleId(Long roleId);

    Optional<AuthUserRole> findByUserIdAndRoleId(Long userId, Long roleId);

    Optional<AuthUserRole> findByUserIdAndIsPrimaryTrue(Long userId);

    @Query("SELECT ur FROM AuthUserRole ur JOIN FETCH ur.role WHERE ur.userId = :userId AND ur.isActive = true")
    List<AuthUserRole> findActiveRolesByUserId(@Param("userId") Long userId);

    @Query("SELECT ur.roleId FROM AuthUserRole ur WHERE ur.userId = :userId AND ur.isActive = true")
    List<Long> findRoleIdsByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    void deleteByUserIdAndRoleId(Long userId, Long roleId);

    void deleteByUserId(Long userId);

    void deleteByRoleId(Long roleId);

    @Modifying
    @Query("DELETE FROM AuthUserRole ur WHERE ur.roleId = :roleId")
    void deleteAllByRoleId(@Param("roleId") Long roleId);
}
