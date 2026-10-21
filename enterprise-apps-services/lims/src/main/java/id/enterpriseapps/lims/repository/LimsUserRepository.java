package id.enterpriseapps.lims.repository;

import id.enterpriseapps.lims.entity.LimsUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Access to the local user snapshot ({@code lims_users}). Keeps LIMS free of
 * any dependency on the core/auth {@code UserRepository}.
 */
@Repository
public interface LimsUserRepository extends JpaRepository<LimsUser, Long> {

    /**
     * Mencari pelaku dari principal Spring Security. Principal berisi email
     * untuk login lokal dan NPK untuk login LDAP, jadi keduanya dicoba dengan
     * nilai yang sama.
     */
    Optional<LimsUser> findFirstByEmailIgnoreCaseOrNpk(String email, String npk);
}
