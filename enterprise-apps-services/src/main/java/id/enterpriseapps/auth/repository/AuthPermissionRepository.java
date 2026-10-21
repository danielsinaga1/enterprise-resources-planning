package id.enterpriseapps.auth.repository;

import id.enterpriseapps.auth.entity.AuthPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuthPermissionRepository extends JpaRepository<AuthPermission, Long> {

    boolean existsByCode(String code);

    List<AuthPermission> findByIsActiveTrue();

    @Query("SELECT p FROM AuthPermission p WHERE p.isActive = true AND p.module = :module ORDER BY p.name")
    List<AuthPermission> findActiveByModule(@Param("module") String module);

    @Query("SELECT DISTINCT p.module FROM AuthPermission p WHERE p.isActive = true ORDER BY p.module")
    List<String> findDistinctModules();
}
