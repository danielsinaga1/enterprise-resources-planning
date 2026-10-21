package id.enterpriseapps.auth.repository;

import id.enterpriseapps.auth.entity.AuthDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthDepartmentRepository extends JpaRepository<AuthDepartment, Long> {
}
