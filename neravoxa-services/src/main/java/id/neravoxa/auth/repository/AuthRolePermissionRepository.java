package id.neravoxa.auth.repository;

import id.neravoxa.auth.entity.AuthRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthRolePermissionRepository extends JpaRepository<AuthRolePermission, Long> {

    Optional<AuthRolePermission> findByRoleIdAndPermissionId(Long roleId, Long permissionId);

    List<AuthRolePermission> findByRoleIdIn(List<Long> roleIds);

    @Query("SELECT rp FROM AuthRolePermission rp JOIN FETCH rp.permission WHERE rp.roleId IN :roleIds")
    List<AuthRolePermission> findWithPermissionsByRoleIds(@Param("roleIds") List<Long> roleIds);

    @Query("SELECT rp FROM AuthRolePermission rp JOIN FETCH rp.permission WHERE rp.roleId = :roleId")
    List<AuthRolePermission> findByRoleIdWithPermission(@Param("roleId") Long roleId);

    @Modifying
    @Query("DELETE FROM AuthRolePermission rp WHERE rp.roleId = :roleId")
    void deleteAllByRoleId(@Param("roleId") Long roleId);

    @Modifying
    @Query("DELETE FROM AuthRolePermission rp WHERE rp.roleId = :roleId AND rp.permissionId = :permissionId")
    void deleteByRoleIdAndPermissionId(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
