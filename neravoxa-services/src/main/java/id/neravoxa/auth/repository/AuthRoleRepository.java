package id.neravoxa.auth.repository;

import id.neravoxa.auth.entity.AuthRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthRoleRepository extends JpaRepository<AuthRole, Long> {

    Optional<AuthRole> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT r FROM AuthRole r WHERE r.isActive = true ORDER BY r.priority DESC, r.name ASC")
    List<AuthRole> findAllActiveOrderByPriority();

    @Query("SELECT r FROM AuthRole r WHERE r.isActive = true AND r.departmentId = :deptId ORDER BY r.priority DESC")
    List<AuthRole> findActiveByDepartmentId(Long deptId);
}
