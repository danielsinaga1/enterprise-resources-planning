package id.enterpriseapps.auth.repository;

import id.enterpriseapps.auth.entity.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthUserRepository extends JpaRepository<AuthUser, Long>, JpaSpecificationExecutor<AuthUser> {

    Optional<AuthUser> findFirstByEmailOrderByIdAsc(String email);
    Optional<AuthUser> findFirstByNpkOrderByIdAsc(String npk);
    Optional<AuthUser> findFirstByNppOrderByIdAsc(String npp);

    boolean existsByEmail(String email);

    List<AuthUser> findByParentId(Long parentId);

    List<AuthUser> findByJobId(Long jobId);

    @Query("SELECT u FROM AuthUser u WHERE u.id <> :userId AND u.status = 1 ORDER BY u.name")
    List<AuthUser> findPotentialSuperiors(@Param("userId") Long userId);

    @Query("SELECT DISTINCT u.id, u.name FROM AuthUser u " +
           "JOIN AuthUserRole ur ON u.id = ur.userId " +
           "JOIN AuthRole r ON ur.roleId = r.id " +
           "WHERE r.title = :roleTitle AND ur.isActive = true " +
           "ORDER BY u.name")
    List<Object[]> findActiveUsersByRoleTitle(@Param("roleTitle") String roleTitle);

    @Query("SELECT DISTINCT u.id, u.name FROM AuthUser u " +
           "JOIN AuthUserRole ur ON u.id = ur.userId " +
           "JOIN AuthRole r ON ur.roleId = r.id " +
           "WHERE r.code = :roleCode AND ur.isActive = true AND (u.status IS NULL OR u.status = 1) " +
           "ORDER BY u.name")
    List<Object[]> findActiveUsersByRoleCode(@Param("roleCode") String roleCode);

    default Optional<AuthUser> findByEmail(String email) {
        return findFirstByEmailOrderByIdAsc(email);
    }

    default Optional<AuthUser> findByNpk(String npk) {
        return findFirstByNpkOrderByIdAsc(npk);
    }

    default Optional<AuthUser> findByNpp(String npp) {
        return findFirstByNppOrderByIdAsc(npp);
    }
}
