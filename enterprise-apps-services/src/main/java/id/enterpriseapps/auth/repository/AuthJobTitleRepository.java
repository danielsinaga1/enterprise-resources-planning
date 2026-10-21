package id.enterpriseapps.auth.repository;

import id.enterpriseapps.auth.entity.AuthJobTitle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthJobTitleRepository extends JpaRepository<AuthJobTitle, Long> {
    Optional<AuthJobTitle> findFirstByNameIgnoreCase(String name);
}
