package id.enterpriseapps.config;

import id.enterpriseapps.auth.entity.AuthUserRole;
import id.enterpriseapps.auth.repository.AuthRoleRepository;
import id.enterpriseapps.auth.repository.AuthUserRoleRepository;
import id.enterpriseapps.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final RolePermissionService rolePermissionService;
    private final AuthRoleRepository authRoleRepository;
    private final AuthUserRoleRepository authUserRoleRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("DataInitializer: syncing roles and permissions...");
            rolePermissionService.syncRolePermissions();
            log.info("DataInitializer: roles and permissions ready.");

            ensureAdminHasSuperAdminRole();
        } catch (Exception e) {
            log.error("DataInitializer: failed to initialize roles and permissions", e);
        }
    }

    private void ensureAdminHasSuperAdminRole() {
        authRoleRepository.findByCode("SUPER_ADMIN").ifPresent(superAdminRole -> {
            Long adminUserId = 1L;
            Long superAdminRoleId = superAdminRole.getId();
            if (!authUserRoleRepository.existsByUserIdAndRoleId(adminUserId, superAdminRoleId)) {
                AuthUserRole userRole = AuthUserRole.builder()
                        .userId(adminUserId)
                        .roleId(superAdminRoleId)
                        .isPrimary(true)
                        .isActive(true)
                        .assignedAt(LocalDateTime.now())
                        .assignedBy("SYSTEM")
                        .build();
                authUserRoleRepository.save(userRole);
                log.info("DataInitializer: assigned SUPER_ADMIN role to admin user (id=1).");
            }
        });
    }
}
