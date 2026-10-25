package id.neravoxa.auth.repository;

import id.neravoxa.auth.entity.AuthDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthDepartmentRepository extends JpaRepository<AuthDepartment, Long> {
}
