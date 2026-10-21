package id.enterpriseapps.erp.adminsetup;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.security.ErpIdentityPort;
import id.enterpriseapps.erp.core.security.ErpIdentityPort.PlatformRole;
import id.enterpriseapps.erp.core.security.ErpIdentityPort.PlatformUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Akses direktori pengguna/role platform (db_auth) lewat {@link ErpIdentityPort}.
 *
 * <p>Endpoint admin (cari & provisi pengguna, sinkron, daftar role platform) tidak bisa bekerja tanpa
 * direktori platform. Bila adapter tidak terpasang atau db_auth gagal diakses, kembalikan 503 yang
 * jelas — jangan diam-diam menganggap semua pengguna "hilang" (sinkron bisa menonaktifkan semua orang).
 */
@Slf4j
@Component
public class ErpPlatformDirectory {

    static final String UNAVAILABLE = "Direktori pengguna platform tidak tersedia.";

    private final ObjectProvider<ErpIdentityPort> identity;

    public ErpPlatformDirectory(ObjectProvider<ErpIdentityPort> identity) {
        this.identity = identity;
    }

    public Optional<PlatformUser> findById(Long id) {
        return call(p -> p.findById(id));
    }

    public Optional<PlatformUser> findByEmail(String email) {
        return call(p -> p.findByEmail(email));
    }

    public List<PlatformUser> search(String query, int limit) {
        return call(p -> p.search(query, limit));
    }

    public List<PlatformRole> listRoles() {
        return call(ErpIdentityPort::listRoles);
    }

    private <T> T call(Function<ErpIdentityPort, T> fn) {
        ErpIdentityPort port;
        try {
            port = identity.getIfAvailable();
        } catch (RuntimeException e) {
            log.error("ERP: adapter identitas platform ambigu/gagal dibuat: {}", e.getMessage());
            throw ErpException.of(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
        if (port == null) {
            throw ErpException.of(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
        try {
            T result = fn.apply(port);
            if (result == null) {
                throw new IllegalStateException("hasil null dari ErpIdentityPort");
            }
            return result;
        } catch (ErpException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("ERP: direktori platform gagal diakses", e);
            throw ErpException.of(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
    }
}
