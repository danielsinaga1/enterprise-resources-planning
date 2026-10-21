package id.enterpriseapps.service;

import id.enterpriseapps.auth.entity.AuthPermission;
import id.enterpriseapps.auth.entity.AuthRole;
import id.enterpriseapps.auth.entity.AuthRolePermission;
import id.enterpriseapps.auth.entity.AuthUser;
import id.enterpriseapps.auth.entity.AuthUserRole;
import id.enterpriseapps.auth.repository.AuthPermissionRepository;
import id.enterpriseapps.auth.repository.AuthRolePermissionRepository;
import id.enterpriseapps.auth.repository.AuthRoleRepository;
import id.enterpriseapps.auth.repository.AuthUserRepository;
import id.enterpriseapps.auth.repository.AuthUserRoleRepository;
import id.enterpriseapps.entity.JobTitleEntity;
import id.enterpriseapps.erp.core.web.ErpMenuProvider;
import id.enterpriseapps.event.UserEvent;
import id.enterpriseapps.repository.DivisionRepository;
import id.enterpriseapps.repository.JobTitleRepository;
import id.enterpriseapps.repository.OriginDepartmentRepository;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.utils.PaginationFilterUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RolePermissionService {

        private final AuthRoleRepository authRoleRepository;
        private final AuthPermissionRepository authPermissionRepository;
        private final AuthUserRoleRepository authUserRoleRepository;
        private final AuthRolePermissionRepository authRolePermissionRepository;
        private final AuthUserRepository authUserRepository;
        /** Menu modul ERP (db_erp) — difilter matriks izin ERP berdasarkan kode role pengguna. */
        private final ErpMenuProvider erpMenuProvider;
        private final OriginDepartmentRepository originDepartmentRepository;
        private final JobTitleRepository jobTitleRepository;
        private final DivisionRepository divisionRepository;
        private final PasswordEncoder passwordEncoder;
        private final PaginationFilterUtil paginationFilterUtil;
        private final IrmsUserSyncService irmsUserSyncService;
        private final EventPublisher eventPublisher;

        // Injected via JPA persistence context, not Lombok constructor
        @PersistenceContext(unitName = "auth")
        private EntityManager entityManager;

        /** Broadcast a user lifecycle change on the auth.user.events topic (best-effort). */
        private void publishUserEvent(UserEvent.Action action, AuthUser user) {
                eventPublisher.publishUserEvent(UserEvent.builder()
                                .action(action)
                                .id(user.getId())
                                .npk(user.getNpk())
                                .npp(user.getNpp())
                                .name(user.getName())
                                .email(user.getEmail())
                                .mobilePhone(user.getMobilePhone())
                                .rfId(user.getRfId())
                                .parentId(user.getParentId())
                                .jobId(user.getJobId())
                                .deptId(user.getDeptId())
                                .divisionId(user.getDivisionId())
                                .status(user.getStatus())
                                .build());
        }

        // =====================================================
        // ROLE MANAGEMENT
        // =====================================================

        @Transactional(transactionManager = "authTransactionManager")
        public Map<String, Object> createRole(Map<String, Object> request) {
                String code = (String) request.get("code");

                if (authRoleRepository.existsByCode(code)) {
                        throw new RuntimeException("Role dengan kode '" + code + "' sudah ada");
                }

                AuthRole role = AuthRole.builder()
                                .code(code.toUpperCase())
                                .name((String) request.get("name"))
                                .description((String) request.get("description"))
                                .departmentId(getLongValue(request, "departmentId"))
                                .isSystemRole(getBooleanValue(request, "isSystemRole", false))
                                .isActive(true)
                                .priority(getIntegerValue(request, "priority", 0))
                                .createdBy((String) request.get("createdBy"))
                                .build();

                AuthRole saved = authRoleRepository.save(role);
                return roleToMap(saved);
        }

        @Transactional(transactionManager = "authTransactionManager")
        public Map<String, Object> updateRole(Long id, Map<String, Object> request) {
                AuthRole role = authRoleRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Role tidak ditemukan"));

                if (request.containsKey("name"))
                        role.setName((String) request.get("name"));
                if (request.containsKey("code") && !role.getIsSystemRole())
                        role.setCode((String) request.get("code"));
                if (request.containsKey("description"))
                        role.setDescription((String) request.get("description"));
                if (request.containsKey("departmentId"))
                        role.setDepartmentId(getLongValue(request, "departmentId"));
                if (request.containsKey("priority"))
                        role.setPriority(getIntegerValue(request, "priority", 0));
                if (request.containsKey("isActive"))
                        role.setIsActive(getBooleanValue(request, "isActive", true));
                role.setUpdatedBy((String) request.get("updatedBy"));

                AuthRole saved = authRoleRepository.save(role);
                return roleToMap(saved);
        }

        public Map<String, Object> getRoleById(Long id) {
                AuthRole role = authRoleRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Role tidak ditemukan"));
                return roleToMap(role);
        }

        public List<Map<String, Object>> getAllRoles() {
                return authRoleRepository.findAllActiveOrderByPriority().stream()
                                .map(this::roleToMap)
                                .collect(Collectors.toList());
        }

        public List<Map<String, Object>> getRolesByDepartment(Long departmentId) {
                return authRoleRepository.findActiveByDepartmentId(departmentId).stream()
                                .map(this::roleToMap)
                                .collect(Collectors.toList());
        }

        @Transactional(transactionManager = "authTransactionManager")
        public void deleteRole(Long id) {
                AuthRole role = authRoleRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Role tidak ditemukan"));

                if (role.getIsSystemRole()) {
                        throw new RuntimeException("System role tidak dapat dihapus");
                }

                authUserRoleRepository.deleteAllByRoleId(id);
                authRolePermissionRepository.deleteAllByRoleId(id);
                authRoleRepository.deleteById(id);
        }

        // =====================================================
        // PERMISSION MANAGEMENT
        // =====================================================

        @Transactional(transactionManager = "authTransactionManager")
        public Map<String, Object> createPermission(Map<String, Object> request) {
                String code = (String) request.get("code");

                if (authPermissionRepository.existsByCode(code)) {
                        throw new RuntimeException("Permission dengan kode '" + code + "' sudah ada");
                }

                AuthPermission permission = AuthPermission.builder()
                                .code(code.toUpperCase())
                                .name((String) request.get("name"))
                                .description((String) request.get("description"))
                                .module((String) request.get("module"))
                                .subModule((String) request.get("subModule"))
                                .action((String) request.get("action"))
                                .isActive(true)
                                .createdBy((String) request.get("createdBy"))
                                .build();

                AuthPermission saved = authPermissionRepository.save(permission);
                return permissionToMap(saved);
        }

        private static final Set<String> DEPRECATED_MODULES = Set.of(
                        "PENGADAAN", "INVENTORY", "KEUANGAN", "ASET",
                        "LAB_REPORT", "DAILY_REPORT", "QUALITY");

        public List<Map<String, Object>> getAllPermissions() {
                return authPermissionRepository.findByIsActiveTrue().stream()
                                .filter(p -> p.getModule() == null
                                                || !DEPRECATED_MODULES.contains(p.getModule()))
                                .map(this::permissionToMap)
                                .collect(Collectors.toList());
        }

        public List<Map<String, Object>> getPermissionsByModule(String module) {
                if (DEPRECATED_MODULES.contains(module)) {
                        return List.of();
                }
                return authPermissionRepository.findActiveByModule(module).stream()
                                .map(this::permissionToMap)
                                .collect(Collectors.toList());
        }

        public List<String> getDistinctModules() {
                return authPermissionRepository.findDistinctModules().stream()
                                .filter(m -> !DEPRECATED_MODULES.contains(m))
                                .collect(Collectors.toList());
        }

        // =====================================================
        // USER MANAGEMENT
        // =====================================================

        /**
         * Look up a single user by their job title (case-insensitive).
         * Cross-datasource: job title lookup in primary (db_nirms), user lookup in auth (db_auth).
         */
        @Transactional(readOnly = true, transactionManager = "authTransactionManager")
        public Map<String, Object> findUserByJobTitle(String title) {
                if (title == null || title.trim().isEmpty()) return null;
                Optional<JobTitleEntity> jobOpt = jobTitleRepository.findFirstByNameIgnoreCase(title.trim());
                if (jobOpt.isEmpty()) return null;
                List<AuthUser> matches = authUserRepository.findByJobId(jobOpt.get().getId());
                if (matches.isEmpty()) return null;
                AuthUser u = matches.get(0);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", u.getId());
                result.put("name", u.getName());
                return result;
        }

        @Transactional(readOnly = true, transactionManager = "authTransactionManager")
        public Response getUsersPaginated(String page, String limit, String sortBy, String sortOrder,
                        String search, String deptIdStr, String statusStr) {
                Pageable pageable = paginationFilterUtil.createPageable(page, limit, sortBy, sortOrder);

                Long deptId = null;
                if (deptIdStr != null && !deptIdStr.trim().isEmpty()) {
                        try { deptId = Long.parseLong(deptIdStr.trim()); } catch (NumberFormatException ignored) {}
                }
                Short status = null;
                if (statusStr != null && !statusStr.trim().isEmpty()) {
                        try { status = Short.parseShort(statusStr.trim()); } catch (NumberFormatException ignored) {}
                }
                final String searchTrimmed = (search != null && !search.trim().isEmpty())
                        ? search.trim().toLowerCase() : null;
                final Long finalDeptId = deptId;
                final Short finalStatus = status;

                Specification<AuthUser> spec = (root, query, cb) -> {
                        List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
                        if (searchTrimmed != null) {
                                String like = "%" + searchTrimmed + "%";
                                predicates.add(cb.or(
                                        cb.like(cb.lower(root.get("name")), like),
                                        cb.like(cb.lower(root.get("email")), like),
                                        cb.like(cb.lower(root.get("npk")), like),
                                        cb.like(cb.lower(root.get("npp")), like)
                                ));
                        }
                        if (finalDeptId != null) {
                                predicates.add(cb.equal(root.get("deptId"), finalDeptId));
                        }
                        if (finalStatus != null) {
                                predicates.add(cb.equal(root.get("status"), finalStatus));
                        }
                        return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                };

                Page<AuthUser> userPage = authUserRepository.findAll(spec, pageable);

                List<Map<String, Object>> userData = userPage.getContent().stream()
                                .map(this::userToDetailMap)
                                .collect(Collectors.toList());

                return Response.builder()
                                .status("SUCCESS")
                                .httpStatus(HttpStatus.OK)
                                .data(userData)
                                .page(userPage.getNumber() + 1)
                                .countData(userPage.getTotalElements())
                                .totalPage(userPage.getTotalPages())
                                .build();
        }

        private Map<String, Object> userToDetailMap(AuthUser user) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", user.getId());
                map.put("npk", user.getNpk());
                map.put("npp", user.getNpp());
                map.put("name", user.getName());
                map.put("email", user.getEmail());
                map.put("mobilePhone", user.getMobilePhone());
                map.put("departmentId", user.getDeptId());
                if (user.getDeptId() != null) {
                        originDepartmentRepository.findById(user.getDeptId())
                                .ifPresent(dept -> map.put("departmentName", dept.getName()));
                }
                if (!map.containsKey("departmentName")) map.put("departmentName", null);
                map.put("divisionId", user.getDivisionId());
                if (user.getDivisionId() != null) {
                        divisionRepository.findById(user.getDivisionId())
                                .ifPresent(div -> map.put("divisionName", div.getName()));
                }
                if (!map.containsKey("divisionName")) map.put("divisionName", null);
                if (user.getJobId() != null) {
                        jobTitleRepository.findById(user.getJobId())
                                .ifPresent(job -> map.put("jobTitle", job.getName()));
                }
                if (!map.containsKey("jobTitle")) map.put("jobTitle", null);

                map.put("parentId", user.getParentId());
                if (user.getParentId() != null) {
                        authUserRepository.findById(user.getParentId())
                                .ifPresent(parent -> map.put("parentName", parent.getName()));
                }
                if (!map.containsKey("parentName")) map.put("parentName", null);

                List<AuthUser> subordinates = authUserRepository.findByParentId(user.getId());
                List<Map<String, Object>> bawahanList = subordinates.stream()
                                .map(sub -> {
                                        Map<String, Object> s = new LinkedHashMap<>();
                                        s.put("id", sub.getId());
                                        s.put("name", sub.getName());
                                        s.put("npk", sub.getNpk());
                                        return s;
                                })
                                .collect(Collectors.toList());
                map.put("subordinates", bawahanList);

                map.put("status", user.getStatus());
                map.put("lastLogin", user.getLastLogin());
                map.put("createdAt", user.getCreatedAt());

                List<AuthUserRole> userRoles = authUserRoleRepository.findByUserIdAndIsActiveTrue(user.getId());
                List<String> roleNames = userRoles.stream()
                                .map(ur -> {
                                        // ur.getRole() is a lazy proxy: `!= null` never catches a dangling role_id,
                                        // it only throws EntityNotFoundException once the proxy is touched. A single
                                        // stale row here 500'd this endpoint for any page containing that user
                                        // (caught live via the department filter on the user management screen).
                                        try {
                                                if (ur.getRole() != null) return ur.getRole().getName();
                                        } catch (jakarta.persistence.EntityNotFoundException ignored) {
                                                // fall through to the defensive lookup below
                                        }
                                        return authRoleRepository.findById(ur.getRoleId())
                                                        .map(AuthRole::getName).orElse("Unknown");
                                })
                                .collect(Collectors.toList());
                map.put("roles", roleNames);
                return map;
        }

        @Transactional(readOnly = true, transactionManager = "authTransactionManager")
        public List<Map<String, Object>> getAllUsers() {
                return authUserRepository.findAll().stream()
                                .map(this::userToDetailMap)
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true, transactionManager = "authTransactionManager")
        public List<Map<String, Object>> getSubordinates(Long userId) {
                authUserRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));
                List<AuthUser> subordinates = authUserRepository.findByParentId(userId);
                return subordinates.stream()
                                .map(sub -> {
                                        Map<String, Object> map = new LinkedHashMap<>();
                                        map.put("id", sub.getId());
                                        map.put("npk", sub.getNpk());
                                        map.put("name", sub.getName());
                                        map.put("email", sub.getEmail());
                                        if (sub.getJobId() != null) {
                                                jobTitleRepository.findById(sub.getJobId())
                                                        .ifPresent(job -> map.put("jobTitle", job.getName()));
                                        }
                                        if (!map.containsKey("jobTitle")) map.put("jobTitle", null);
                                        map.put("status", sub.getStatus());
                                        return map;
                                })
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true, transactionManager = "authTransactionManager")
        public List<Map<String, Object>> getUsersDropdown() {
                return authUserRepository.findAll().stream()
                                .filter(u -> u.getStatus() != null && u.getStatus() == 1)
                                .map(u -> {
                                        Map<String, Object> map = new LinkedHashMap<>();
                                        map.put("id", u.getId());
                                        map.put("name", u.getName());
                                        map.put("npk", u.getNpk());
                                        return map;
                                })
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true, transactionManager = "authTransactionManager")
        public List<Map<String, Object>> getUsersDropdownByRole(String roleTitle) {
                List<Object[]> rows = authUserRepository.findActiveUsersByRoleTitle(roleTitle);
                return rows.stream()
                                .map(row -> {
                                        Map<String, Object> map = new LinkedHashMap<>();
                                        map.put("id", ((Number) row[0]).longValue());
                                        map.put("name", (String) row[1]);
                                        return map;
                                })
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true, transactionManager = "authTransactionManager")
        public List<Map<String, Object>> getUsersDropdownByRoleCode(String roleCode) {
                List<Object[]> rows = authUserRepository.findActiveUsersByRoleCode(roleCode);
                return rows.stream()
                                .map(row -> {
                                        Map<String, Object> map = new LinkedHashMap<>();
                                        map.put("id", ((Number) row[0]).longValue());
                                        map.put("name", (String) row[1]);
                                        return map;
                                })
                                .collect(Collectors.toList());
        }

        @Transactional(transactionManager = "authTransactionManager")
        public Map<String, Object> createUser(Map<String, Object> request) {
                String npk = (String) request.get("npk");
                String email = (String) request.get("email");

                if (email != null && authUserRepository.existsByEmail(email)) {
                        throw new RuntimeException("Email '" + email + "' sudah digunakan");
                }

                if (npk != null && authUserRepository.findByNpk(npk).isPresent()) {
                        throw new RuntimeException("NPK '" + npk + "' sudah digunakan");
                }

                String defaultPassword = (String) request.getOrDefault("password", "password123");

                AuthUser user = new AuthUser();
                user.setNpk(npk);
                user.setNpp((String) request.get("npp"));
                user.setName((String) request.get("name"));
                user.setEmail(email);
                user.setMobilePhone((String) request.get("mobilePhone"));
                user.setParentId(getLongValue(request, "parentId"));
                user.setJobId(getLongValue(request, "jobId"));
                user.setDeptId(getLongValue(request, "deptId"));
                user.setDivisionId(getLongValue(request, "divisionId"));
                user.setPassword(passwordEncoder.encode(defaultPassword));
                user.setStatus((short) 1);
                user.setAttemptLoginFailed(0);
                user.setCreatedBy((String) request.getOrDefault("createdBy", "SYSTEM"));

                AuthUser saved = authUserRepository.save(user);

                Long roleId = getLongValue(request, "roleId");
                if (roleId != null) {
                        assignRoleToUser(saved.getId(), roleId,
                                        (String) request.getOrDefault("createdBy", "SYSTEM"));
                }

                Map<String, Object> result = userToMap(saved);
                result.put("defaultPassword", defaultPassword);

                // Sync snapshot to irms_users (best-effort, non-blocking)
                irmsUserSyncService.syncCreate(saved.getId(), saved.getNpk(), saved.getNpp(),
                        saved.getName(), saved.getEmail(), saved.getMobilePhone(), saved.getRfId(),
                        saved.getParentId(), saved.getJobId(), saved.getDeptId(), saved.getDivisionId(),
                        saved.getStatus());
                // Broadcast for other read-side snapshots (e.g. lims_users)
                publishUserEvent(UserEvent.Action.CREATED, saved);

                return result;
        }

        @Transactional(transactionManager = "authTransactionManager")
        public Map<String, Object> updateUser(Long id, Map<String, Object> request) {
                AuthUser user = authUserRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));

                if (request.containsKey("name"))
                        user.setName((String) request.get("name"));
                if (request.containsKey("email")) {
                        String newEmail = (String) request.get("email");
                        if (newEmail != null && !newEmail.equals(user.getEmail())
                                        && authUserRepository.existsByEmail(newEmail)) {
                                throw new RuntimeException("Email '" + newEmail + "' sudah digunakan");
                        }
                        user.setEmail(newEmail);
                }
                if (request.containsKey("npp"))
                        user.setNpp((String) request.get("npp"));
                if (request.containsKey("npk")) {
                        String newNpk = (String) request.get("npk");
                        if (newNpk != null && !newNpk.equals(user.getNpk())
                                        && authUserRepository.findByNpk(newNpk).isPresent()) {
                                throw new RuntimeException("NPK '" + newNpk + "' sudah digunakan");
                        }
                        user.setNpk(newNpk);
                }
                if (request.containsKey("mobilePhone"))
                        user.setMobilePhone((String) request.get("mobilePhone"));
                if (request.containsKey("parentId")) {
                        Long newParentId = getLongValue(request, "parentId");
                        if (newParentId != null && newParentId.equals(id)) {
                                throw new RuntimeException("User tidak bisa menjadi atasan dirinya sendiri");
                        }
                        user.setParentId(newParentId);
                }
                if (request.containsKey("jobId"))
                        user.setJobId(getLongValue(request, "jobId"));
                if (request.containsKey("deptId"))
                        user.setDeptId(getLongValue(request, "deptId"));
                if (request.containsKey("divisionId"))
                        user.setDivisionId(getLongValue(request, "divisionId"));
                if (request.containsKey("password")) {
                        String newPassword = (String) request.get("password");
                        if (newPassword != null && !newPassword.isEmpty()) {
                                user.setPassword(passwordEncoder.encode(newPassword));
                        }
                }
                user.setUpdatedBy((String) request.getOrDefault("updatedBy", "SYSTEM"));

                AuthUser saved = authUserRepository.save(user);

                // Sync snapshot to irms_users (best-effort, non-blocking)
                irmsUserSyncService.syncUpdate(saved.getId(), saved.getNpk(), saved.getNpp(),
                        saved.getName(), saved.getEmail(), saved.getMobilePhone(), saved.getRfId(),
                        saved.getParentId(), saved.getJobId(), saved.getDeptId(), saved.getDivisionId(),
                        saved.getStatus());
                // Broadcast for other read-side snapshots (e.g. lims_users)
                publishUserEvent(UserEvent.Action.UPDATED, saved);

                return userToMap(saved);
        }

        @Transactional(transactionManager = "authTransactionManager")
        public void deleteUser(Long id) {
                AuthUser user = authUserRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));
                authUserRoleRepository.deleteByUserId(id);
                authUserRepository.delete(user);
                // Sync snapshot deletion to irms_users (best-effort)
                irmsUserSyncService.syncDelete(id);
                // Broadcast for other read-side snapshots (e.g. lims_users)
                publishUserEvent(UserEvent.Action.DELETED, user);
        }

        @Transactional(transactionManager = "authTransactionManager")
        public Map<String, Object> toggleUserStatus(Long id) {
                AuthUser user = authUserRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));
                short newStatus = (user.getStatus() != null && user.getStatus() == 1) ? (short) 0 : (short) 1;
                user.setStatus(newStatus);
                user.setUpdatedBy("SYSTEM");
                AuthUser saved = authUserRepository.save(user);
                // Sync status snapshot to irms_users (best-effort)
                irmsUserSyncService.syncStatus(id, newStatus);
                // Broadcast for other read-side snapshots (e.g. lims_users)
                publishUserEvent(UserEvent.Action.STATUS_CHANGED, saved);
                return userToMap(saved);
        }

        @Transactional(transactionManager = "authTransactionManager")
        public Map<String, Object> resetUserPassword(Long id) {
                AuthUser user = authUserRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));
                String defaultPassword = "password123";
                user.setPassword(passwordEncoder.encode(defaultPassword));
                user.setAttemptLoginFailed(0);
                user.setUpdatedBy("SYSTEM");
                authUserRepository.save(user);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("userId", id);
                result.put("message", "Password berhasil direset");
                result.put("defaultPassword", defaultPassword);
                return result;
        }

        private Map<String, Object> userToMap(AuthUser user) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", user.getId());
                map.put("npp", user.getNpp());
                map.put("npk", user.getNpk());
                map.put("name", user.getName());
                map.put("email", user.getEmail());
                map.put("mobilePhone", user.getMobilePhone());
                map.put("departmentId", user.getDeptId());
                if (user.getDeptId() != null) {
                        originDepartmentRepository.findById(user.getDeptId())
                                .ifPresent(dept -> map.put("departmentName", dept.getName()));
                }
                if (!map.containsKey("departmentName")) map.put("departmentName", null);
                map.put("divisionId", user.getDivisionId());
                if (user.getDivisionId() != null) {
                        divisionRepository.findById(user.getDivisionId())
                                .ifPresent(div -> map.put("divisionName", div.getName()));
                }
                if (!map.containsKey("divisionName")) map.put("divisionName", null);
                map.put("parentId", user.getParentId());
                if (user.getParentId() != null) {
                        authUserRepository.findById(user.getParentId())
                                .ifPresent(parent -> map.put("parentName", parent.getName()));
                }
                if (!map.containsKey("parentName")) map.put("parentName", null);
                map.put("status", user.getStatus());
                map.put("lastLogin", user.getLastLogin());
                map.put("createdAt", user.getCreatedAt());
                return map;
        }

        // =====================================================
        // USER ROLE ASSIGNMENT
        // =====================================================

        @Transactional(transactionManager = "authTransactionManager")
        public Map<String, Object> assignRoleToUser(Long userId, Long roleId, String assignedBy) {
                authUserRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));

                authRoleRepository.findById(roleId)
                                .orElseThrow(() -> new RuntimeException("Role tidak ditemukan"));

                if (authUserRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
                        throw new RuntimeException("User sudah memiliki role ini");
                }

                boolean hasPrimary = authUserRoleRepository.findByUserIdAndIsPrimaryTrue(userId).isPresent();

                AuthUserRole userRole = AuthUserRole.builder()
                                .userId(userId)
                                .roleId(roleId)
                                .isPrimary(!hasPrimary)
                                .isActive(true)
                                .assignedAt(LocalDateTime.now())
                                .assignedBy(assignedBy)
                                .build();

                AuthUserRole saved = authUserRoleRepository.save(userRole);
                return userRoleToMap(saved);
        }

        @Transactional(transactionManager = "authTransactionManager")
        public void removeRoleFromUser(Long userId, Long roleId) {
                AuthUserRole userRole = authUserRoleRepository.findByUserIdAndRoleId(userId, roleId)
                                .orElseThrow(() -> new RuntimeException("User role tidak ditemukan"));

                authUserRoleRepository.delete(userRole);
        }

        @Transactional(transactionManager = "authTransactionManager")
        public void setPrimaryRole(Long userId, Long roleId) {
                authUserRoleRepository.findByUserIdAndIsPrimaryTrue(userId)
                                .ifPresent(ur -> {
                                        ur.setIsPrimary(false);
                                        authUserRoleRepository.save(ur);
                                });

                AuthUserRole userRole = authUserRoleRepository.findByUserIdAndRoleId(userId, roleId)
                                .orElseThrow(() -> new RuntimeException("User role tidak ditemukan"));
                userRole.setIsPrimary(true);
                authUserRoleRepository.save(userRole);
        }

        @Transactional(readOnly = true, transactionManager = "authTransactionManager")
        public List<Map<String, Object>> getUserRoles(Long userId) {
                return authUserRoleRepository.findActiveRolesByUserId(userId).stream()
                                .map(this::userRoleToMap)
                                .collect(Collectors.toList());
        }

        // =====================================================
        // ROLE PERMISSION ASSIGNMENT
        // =====================================================

        @Transactional(transactionManager = "authTransactionManager")
        public Map<String, Object> assignPermissionToRole(Map<String, Object> request) {
                Long roleId = getLongValue(request, "roleId");
                Long permissionId = getLongValue(request, "permissionId");

                authRoleRepository.findById(roleId)
                                .orElseThrow(() -> new RuntimeException("Role tidak ditemukan"));
                authPermissionRepository.findById(permissionId)
                                .orElseThrow(() -> new RuntimeException("Permission tidak ditemukan"));

                Optional<AuthRolePermission> existing = authRolePermissionRepository
                                .findByRoleIdAndPermissionId(roleId, permissionId);

                AuthRolePermission rolePermission;
                if (existing.isPresent()) {
                        rolePermission = existing.get();
                } else {
                        rolePermission = new AuthRolePermission();
                        rolePermission.setRoleId(roleId);
                        rolePermission.setPermissionId(permissionId);
                        rolePermission.setCreatedBy((String) request.get("assignedBy"));
                }

                rolePermission.setCanCreate(getBooleanValue(request, "canCreate", false));
                rolePermission.setCanRead(getBooleanValue(request, "canRead", false));
                rolePermission.setCanUpdate(getBooleanValue(request, "canUpdate", false));
                rolePermission.setCanDelete(getBooleanValue(request, "canDelete", false));
                rolePermission.setCanApprove(getBooleanValue(request, "canApprove", false));
                rolePermission.setCanExport(getBooleanValue(request, "canExport", false));

                AuthRolePermission saved = authRolePermissionRepository.save(rolePermission);
                return rolePermissionToMap(saved);
        }

        @Transactional(transactionManager = "authTransactionManager")
        public void removePermissionFromRole(Long roleId, Long permissionId) {
                authRolePermissionRepository.deleteByRoleIdAndPermissionId(roleId, permissionId);
        }

        /**
         * Bulk save role permissions — replaces all permissions for a given role.
         */
        @Transactional(transactionManager = "authTransactionManager")
        public List<Map<String, Object>> bulkSaveRolePermissions(Long roleId,
                        List<Map<String, Object>> permissionList) {
                authRoleRepository.findById(roleId)
                                .orElseThrow(() -> new RuntimeException("Role tidak ditemukan"));

                authRolePermissionRepository.deleteAllByRoleId(roleId);
                entityManager.flush();
                entityManager.clear();

                List<AuthRolePermission> entities = new ArrayList<>();
                for (Map<String, Object> perm : permissionList) {
                        Long permissionId = getLongValue(perm, "permissionId");
                        if (permissionId == null)
                                continue;

                        boolean hasAny = getBooleanValue(perm, "canCreate", false)
                                        || getBooleanValue(perm, "canRead", false)
                                        || getBooleanValue(perm, "canUpdate", false)
                                        || getBooleanValue(perm, "canDelete", false)
                                        || getBooleanValue(perm, "canApprove", false)
                                        || getBooleanValue(perm, "canExport", false);

                        if (!hasAny)
                                continue;

                        entities.add(buildRolePermission(roleId, permissionId,
                                        getBooleanValue(perm, "canCreate", false),
                                        getBooleanValue(perm, "canRead", false),
                                        getBooleanValue(perm, "canUpdate", false),
                                        getBooleanValue(perm, "canDelete", false),
                                        getBooleanValue(perm, "canApprove", false),
                                        getBooleanValue(perm, "canExport", false)));
                }

                List<AuthRolePermission> saved = authRolePermissionRepository.saveAll(entities);
                return saved.stream().map(this::rolePermissionToMap).collect(Collectors.toList());
        }

        /**
         * Get sidebar menu structure filtered by user's permissions.
         */
        @Transactional(readOnly = true, transactionManager = "authTransactionManager")
        public List<Map<String, Object>> getUserMenu(Long userId) {
                List<Long> roleIds = authUserRoleRepository.findRoleIdsByUserId(userId);

                boolean isSuperAdmin = false;
                if (!roleIds.isEmpty()) {
                        isSuperAdmin = authRoleRepository.findAllById(roleIds).stream()
                                        .anyMatch(r -> "SUPER_ADMIN".equals(r.getCode()));
                }

                Set<String> accessibleModules = new LinkedHashSet<>();
                Set<String> readableCodes = new LinkedHashSet<>();

                if (isSuperAdmin) {
                        accessibleModules.addAll(Arrays.asList("DASHBOARD", "INCIDENT", "SAFETY",
                                        "RISK", "LIMS", "SMARTDOCS", "PROCUREMENT", "ORGANISASI", "SYSTEM"));
                } else if (!roleIds.isEmpty()) {
                        List<AuthRolePermission> rolePermissions = authRolePermissionRepository
                                        .findWithPermissionsByRoleIds(roleIds);
                        for (AuthRolePermission rp : rolePermissions) {
                                if (rp.getPermission() != null && rp.getCanRead()) {
                                        accessibleModules.add(rp.getPermission().getModule());
                                        readableCodes.add(rp.getPermission().getCode());
                                }
                        }
                }

                List<Map<String, Object>> menu = new ArrayList<>();

                // MAIN
                if (accessibleModules.contains("DASHBOARD")) {
                        Map<String, Object> main = new LinkedHashMap<>();
                        main.put("label", "MAIN");
                        main.put("items", List.of(
                                        Map.of("label", "Dashboard", "icon", "pi pi-th-large",
                                                        "to", "/dashboard")));
                        menu.add(main);
                }

                // IRMS
                List<Map<String, Object>> irmsItems = new ArrayList<>();
                if (accessibleModules.contains("INCIDENT") || accessibleModules.contains("SAFETY")
                                || accessibleModules.contains("RISK")) {
                        irmsItems.add(Map.of("label", "Dashboard IRMS", "icon", "pi pi-chart-bar",
                                        "to", "/dashboard/irms"));
                }
                if (accessibleModules.contains("INCIDENT")) {
                        irmsItems.add(Map.of("label", "Laporan Insiden", "icon",
                                        "pi pi-exclamation-triangle",
                                        "items", List.of(
                                                        Map.of("label", "Daftar Insiden", "to",
                                                                        "/dashboard/irms/incident-reports"),
                                                        Map.of("label", "Buat Laporan", "to",
                                                                        "/dashboard/irms/incident-reports/create"))));
                        // Hanya tampil bila user punya permission Tugas Insiden di matrix.
                        if (isSuperAdmin || readableCodes.contains("INCIDENT_FOLLOWUP_ASSIGN")) {
                                irmsItems.add(Map.of("label", "Tugas Insiden", "icon",
                                                "pi pi-list-check",
                                                "to", "/dashboard/irms/incident-tasks"));
                        }
                }
                if (accessibleModules.contains("SAFETY")) {
                        irmsItems.add(Map.of("label", "Observasi Keselamatan", "icon", "pi pi-eye",
                                        "items", List.of(
                                                        Map.of("label", "Daftar Observasi", "to",
                                                                        "/dashboard/irms/safety-observations"),
                                                        Map.of("label", "Buat Observasi", "to",
                                                                        "/dashboard/irms/safety-observations/create"),
                                                        Map.of("label", "Tugas Observasi", "to",
                                                                        "/dashboard/irms/observation-tasks"))));
                }
                if (!irmsItems.isEmpty()) {
                        Map<String, Object> irms = new LinkedHashMap<>();
                        irms.put("label", "IRMS");
                        irms.put("items", irmsItems);
                        menu.add(irms);
                }

                // PENGADAAN
                if (accessibleModules.contains("PROCUREMENT")) {
                        Map<String, Object> procurement = new LinkedHashMap<>();
                        procurement.put("label", "PENGADAAN");
                        procurement.put("items", List.of(
                                        Map.of("label", "Monitoring PR", "icon", "pi pi-shopping-cart",
                                                        "to", "/dashboard/procurement/purchase-requisitions")));
                        menu.add(procurement);
                }

                // LIMS
                List<Map<String, Object>> limsItems = new ArrayList<>();
                if (accessibleModules.contains("LIMS")) {
                        limsItems.add(Map.of("label", "Dashboard LIMS", "icon", "pi pi-chart-bar",
                                        "to", "/dashboard/lims"));
                        limsItems.add(Map.of("label", "Laporan Laboratorium", "icon", "pi pi-file",
                                        "items", List.of(
                                                        Map.of("label", "Daftar Laporan Lab", "to",
                                                                        "/dashboard/lims/laboratory-reports"),
                                                        Map.of("label", "Buat Laporan Lab", "to",
                                                                        "/dashboard/lims/laboratory-reports/create"))));
                        limsItems.add(Map.of("label", "Laporan Harian", "icon", "pi pi-calendar",
                                        "items", List.of(
                                                        Map.of("label", "Daftar Laporan Harian", "to",
                                                                        "/dashboard/lims/daily-reports"),
                                                        Map.of("label", "Buat Laporan Harian", "to",
                                                                        "/dashboard/lims/daily-reports/create"))));
                        limsItems.add(Map.of("label", "Inventaris Alat", "icon", "pi pi-box",
                                        "items", List.of(
                                                        Map.of("label", "Dashboard Inventaris", "to",
                                                                        "/dashboard/lims/inventory/dashboard"),
                                                        Map.of("label", "Daftar Inventaris", "to",
                                                                        "/dashboard/lims/inventory"),
                                                        Map.of("label", "Tambah Alat", "to",
                                                                        "/dashboard/lims/inventory/create"))));
                        limsItems.add(Map.of("label", "Permission Matrix", "icon", "pi pi-shield",
                                        "to", "/dashboard/lims/permission-matrix"));
                }
                if (!limsItems.isEmpty()) {
                        Map<String, Object> lims = new LinkedHashMap<>();
                        lims.put("label", "LIMS");
                        lims.put("items", limsItems);
                        menu.add(lims);
                }

                // SMARTDOCS — dokumen hasil OCR (db_smartdocs)
                // Satu entri yang bisa dibuka-tutup, bukan lima baris datar: kelima
                // halaman ini selalu dipakai bersama, dan mendatarkan semuanya membuat
                // sidebar memanjang tanpa menambah kejelasan.
                List<Map<String, Object>> smartDocsItems = new ArrayList<>();
                if (accessibleModules.contains("SMARTDOCS")) {
                        smartDocsItems.add(Map.of("label", "SmartDocs", "icon", "pi pi-folder-open",
                                        "items", List.of(
                                                        Map.of("label", "Dashboard", "to",
                                                                        "/dashboard/smartdocs"),
                                                        Map.of("label", "Daftar Dokumen", "to",
                                                                        "/dashboard/smartdocs/documents"),
                                                        Map.of("label", "Unggah & Pindai", "to",
                                                                        "/dashboard/smartdocs/upload"),
                                                        Map.of("label", "Laporan", "to",
                                                                        "/dashboard/smartdocs/reports"),
                                                        Map.of("label", "Log Aktivitas", "to",
                                                                        "/dashboard/smartdocs/activity"))));
                }
                if (!smartDocsItems.isEmpty()) {
                        Map<String, Object> smartDocs = new LinkedHashMap<>();
                        smartDocs.put("label", "SMARTDOCS");
                        smartDocs.put("items", smartDocsItems);
                        menu.add(smartDocs);
                }

                // ERP — port fitur ENTARIS. Visibilitas per item mengikuti matriks Hak Akses ERP
                // (db_erp), bukan auth_permissions, sehingga seksi ini hanya muncul untuk pengguna
                // yang punya role ERP (atau SUPER_ADMIN).
                try {
                        List<String> roleCodes = roleIds.isEmpty() ? List.of()
                                        : authRoleRepository.findAllById(roleIds).stream()
                                                        .map(r -> r.getCode() != null && !r.getCode().isBlank()
                                                                        ? r.getCode() : r.getTitle())
                                                        .filter(Objects::nonNull)
                                                        .collect(Collectors.toList());
                        menu.addAll(erpMenuProvider.menuFor(roleCodes));
                } catch (RuntimeException e) {
                        log.warn("Menu ERP tidak tersedia: {}", e.getMessage());
                }

                // PENGATURAN
                List<Map<String, Object>> settingsItems = new ArrayList<>();
                if (accessibleModules.contains("SYSTEM")) {
                        settingsItems.add(Map.of("label", "Manajemen User", "icon", "pi pi-user-edit",
                                        "items", List.of(
                                                        Map.of("label", "Daftar User", "to",
                                                                        "/dashboard/settings/users"),
                                                        Map.of("label", "Role & Permission", "to",
                                                                        "/dashboard/settings/roles"),
                                                        Map.of("label", "Hak Akses", "to",
                                                                        "/dashboard/settings/access-management"))));
                        List<Map<String, Object>> masterDataItems = new ArrayList<>(List.of(
                                        Map.of("label", "Area", "to",
                                                        "/dashboard/settings/master-data/areas"),
                                        Map.of("label", "Kategori Insiden", "to",
                                                        "/dashboard/settings/master-data/categories"),
                                        Map.of("label", "Klasifikasi", "to",
                                                        "/dashboard/settings/master-data/classifications"),
                                        Map.of("label", "Akar Masalah", "to",
                                                        "/dashboard/settings/master-data/root-causes"),
                                        Map.of("label", "Region", "to",
                                                        "/dashboard/settings/master-data/regions"),
                                        Map.of("label", "Dept. Asal", "to",
                                                        "/dashboard/settings/master-data/origin-departments"),
                                        Map.of("label", "Dept. Tujuan", "to",
                                                        "/dashboard/settings/master-data/designation-departments")));
                        masterDataItems.add(Map.of("label", "Parameter Uji", "to",
                                        "/dashboard/settings/master-data/lims-parameters"));
                        masterDataItems.add(Map.of("label", "Jenis Uji", "to",
                                        "/dashboard/settings/master-data/lims-test-types"));
                        masterDataItems.add(Map.of("label", "Metode", "to",
                                        "/dashboard/settings/master-data/lims-methods"));
                        masterDataItems.add(Map.of("label", "Standar", "to",
                                        "/dashboard/settings/master-data/lims-standards"));
                        masterDataItems.add(Map.of("label", "Satuan", "to",
                                        "/dashboard/settings/master-data/lims-units"));
                        masterDataItems.add(Map.of("label", "Dept. Lab", "to",
                                        "/dashboard/settings/master-data/lims-departments"));
                        masterDataItems.add(Map.of("label", "Hasil Lab", "to",
                                        "/dashboard/settings/master-data/lims-results"));
                        masterDataItems.add(Map.of("label", "Senyawa Kimia", "to",
                                        "/dashboard/settings/master-data/lims-compounds"));
                        settingsItems.add(Map.of("label", "Master Data", "icon", "pi pi-database",
                                        "items", masterDataItems));
                        settingsItems.add(Map.of("label", "Sistem", "icon", "pi pi-cog",
                                        "items", List.of(
                                                        Map.of("label", "Pengaturan Umum", "to",
                                                                        "/dashboard/settings/general"),
                                                        Map.of("label", "Audit Log", "to",
                                                                        "/dashboard/settings/audit-log"),
                                                        Map.of("label", "Email Template", "to",
                                                                        "/dashboard/settings/email-templates"))));
                }
                if (!settingsItems.isEmpty()) {
                        Map<String, Object> settings = new LinkedHashMap<>();
                        settings.put("label", "PENGATURAN");
                        settings.put("items", settingsItems);
                        menu.add(settings);
                }

                return menu;
        }

        public List<Map<String, Object>> getRolePermissions(Long roleId) {
                return authRolePermissionRepository.findByRoleIdWithPermission(roleId).stream()
                                .map(this::rolePermissionToMap)
                                .collect(Collectors.toList());
        }

        // =====================================================
        // GET USER PERMISSIONS (Main method for authorization)
        // =====================================================

        public Map<String, Object> getUserPermissions(Long userId) {
                List<Long> roleIds = authUserRoleRepository.findRoleIdsByUserId(userId);

                if (roleIds.isEmpty()) {
                        return Map.of(
                                        "userId", userId,
                                        "roles", Collections.emptyList(),
                                        "permissions", Collections.emptyList(),
                                        "modules", Collections.emptyList());
                }

                List<AuthRole> roles = authRoleRepository.findAllById(roleIds);
                List<AuthRolePermission> rolePermissions = authRolePermissionRepository
                                .findWithPermissionsByRoleIds(roleIds);

                Map<String, Map<String, Boolean>> permissionMap = new LinkedHashMap<>();
                Set<String> modules = new LinkedHashSet<>();

                for (AuthRolePermission rp : rolePermissions) {
                        AuthPermission p = rp.getPermission();
                        if (p == null)
                                continue;

                        modules.add(p.getModule());

                        String permCode = p.getCode();
                        Map<String, Boolean> access = permissionMap.computeIfAbsent(permCode, k -> new HashMap<>());

                        access.merge("canCreate", rp.getCanCreate(), (a, b) -> a || b);
                        access.merge("canRead", rp.getCanRead(), (a, b) -> a || b);
                        access.merge("canUpdate", rp.getCanUpdate(), (a, b) -> a || b);
                        access.merge("canDelete", rp.getCanDelete(), (a, b) -> a || b);
                        access.merge("canApprove", rp.getCanApprove(), (a, b) -> a || b);
                        access.merge("canExport", rp.getCanExport(), (a, b) -> a || b);
                }

                List<Map<String, Object>> permissionList = new ArrayList<>();
                for (AuthRolePermission rp : rolePermissions) {
                        AuthPermission p = rp.getPermission();
                        if (p == null)
                                continue;

                        Map<String, Object> perm = new LinkedHashMap<>();
                        perm.put("code", p.getCode());
                        perm.put("name", p.getName());
                        perm.put("module", p.getModule());
                        perm.put("subModule", p.getSubModule());
                        perm.put("action", p.getAction());
                        perm.put("access", permissionMap.get(p.getCode()));
                        permissionList.add(perm);
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("userId", userId);
                result.put("roles", roles.stream().map(r -> Map.of(
                                "id", r.getId(),
                                "code", r.getCode(),
                                "name", r.getName())).collect(Collectors.toList()));
                result.put("permissions", permissionList);
                result.put("modules", new ArrayList<>(modules));

                return result;
        }

        // =====================================================
        // CHECK PERMISSION
        // =====================================================

        public boolean hasPermission(Long userId, String permissionCode, String action) {
                List<Long> roleIds = authUserRoleRepository.findRoleIdsByUserId(userId);
                if (roleIds.isEmpty())
                        return false;

                boolean isSuperAdmin = authRoleRepository.findAllById(roleIds).stream()
                                .anyMatch(r -> "SUPER_ADMIN".equals(r.getCode()));
                if (isSuperAdmin)
                        return true;

                List<AuthRolePermission> rolePermissions = authRolePermissionRepository.findByRoleIdIn(roleIds);

                for (AuthRolePermission rp : rolePermissions) {
                        if (rp.getPermission() != null &&
                                        rp.getPermission().getCode().equals(permissionCode)) {
                                return switch (action.toLowerCase()) {
                                        case "create"  -> rp.getCanCreate();
                                        case "read"    -> rp.getCanRead();
                                        case "update"  -> rp.getCanUpdate();
                                        case "delete"  -> rp.getCanDelete();
                                        case "approve" -> rp.getCanApprove();
                                        case "export"  -> rp.getCanExport();
                                        default        -> false;
                                };
                        }
                }
                return false;
        }

        public boolean hasModuleAccess(Long userId, String module) {
                List<Long> roleIds = authUserRoleRepository.findRoleIdsByUserId(userId);
                if (roleIds.isEmpty())
                        return false;

                boolean isSuperAdmin = authRoleRepository.findAllById(roleIds).stream()
                                .anyMatch(r -> "SUPER_ADMIN".equals(r.getCode()));
                if (isSuperAdmin)
                        return true;

                List<AuthRolePermission> rolePermissions = authRolePermissionRepository
                                .findWithPermissionsByRoleIds(roleIds);

                return rolePermissions.stream()
                                .anyMatch(rp -> rp.getPermission() != null &&
                                                rp.getPermission().getModule().equalsIgnoreCase(module) &&
                                                rp.getCanRead());
        }

        // =====================================================
        // INITIALIZE DEFAULT ROLES & PERMISSIONS
        // =====================================================

        @Transactional(transactionManager = "authTransactionManager")
        public void initializeDefaultRolesAndPermissions() {
                if (authRoleRepository.count() > 0) {
                        log.info("Roles already initialized, skipping...");
                        return;
                }

                log.info("Initializing default roles and permissions...");

                List<AuthPermission> permissions = createDefaultPermissions();
                authPermissionRepository.saveAll(permissions);

                List<AuthRole> roles = createDefaultRoles();
                authRoleRepository.saveAll(roles);

                assignDefaultPermissionsToRoles();

                log.info("Default roles and permissions initialized successfully");
        }

        @Transactional(transactionManager = "authTransactionManager")
        public void syncRolePermissions() {
                log.info("Syncing role permissions...");

                authPermissionRepository.findAll().stream()
                                .filter(p -> p.getCode() == null || p.getCode().isBlank())
                                .forEach(p -> authPermissionRepository.delete(p));

                Map<String, AuthPermission> existingPerms = authPermissionRepository.findAll().stream()
                                .filter(p -> p.getCode() != null && !p.getCode().isBlank())
                                .collect(Collectors.toMap(AuthPermission::getCode, p -> p, (a, b) -> a));
                List<AuthPermission> newPermissions = createDefaultPermissions().stream()
                                .filter(p -> !existingPerms.containsKey(p.getCode()))
                                .collect(Collectors.toList());
                if (!newPermissions.isEmpty()) {
                        authPermissionRepository.saveAll(newPermissions);
                        log.info("Added {} new permissions", newPermissions.size());
                }

                Map<String, AuthRole> existingRoles = authRoleRepository.findAll().stream()
                                .filter(r -> r.getCode() != null && !r.getCode().isBlank())
                                .collect(Collectors.toMap(AuthRole::getCode, r -> r, (a, b) -> a));
                List<AuthRole> newRoles = createDefaultRoles().stream()
                                .filter(r -> !existingRoles.containsKey(r.getCode()))
                                .collect(Collectors.toList());
                if (!newRoles.isEmpty()) {
                        authRoleRepository.saveAll(newRoles);
                        log.info("Added {} new roles", newRoles.size());
                }

                authRolePermissionRepository.deleteAll();
                // Force Hibernate to flush DELETEs to DB before we issue INSERTs
                entityManager.flush();
                entityManager.clear();

                List<String> deprecatedModules = List.of("PENGADAAN", "INVENTORY", "KEUANGAN", "ASET",
                    "LAB_REPORT", "DAILY_REPORT", "QUALITY");
                List<AuthPermission> deprecatedPerms = authPermissionRepository.findAll().stream()
                                .filter(p -> p.getModule() != null && deprecatedModules.contains(p.getModule()))
                                .collect(Collectors.toList());
                if (!deprecatedPerms.isEmpty()) {
                        authPermissionRepository.deleteAll(deprecatedPerms);
                        log.info("Removed {} deprecated permissions", deprecatedPerms.size());
                }

                Set<String> validCodes = createDefaultPermissions().stream()
                                .map(AuthPermission::getCode)
                                .collect(Collectors.toSet());
                List<AuthPermission> orphanPerms = authPermissionRepository.findAll().stream()
                                .filter(p -> p.getCode() != null && !p.getCode().isBlank()
                                                && !validCodes.contains(p.getCode()))
                                .collect(Collectors.toList());
                if (!orphanPerms.isEmpty()) {
                        authPermissionRepository.deleteAll(orphanPerms);
                        log.info("Removed {} orphan permissions", orphanPerms.size());
                }

                assignDefaultPermissionsToRoles();

                log.info("Role permissions synced successfully");
        }

        private List<AuthPermission> createDefaultPermissions() {
                List<AuthPermission> permissions = new ArrayList<>();

                permissions.add(buildPermission("DASHBOARD_VIEW", "Lihat Dashboard", "DASHBOARD", null, "VIEW"));

                permissions.add(buildPermission("INCIDENT_REPORT_VIEW", "Lihat Laporan Insiden", "INCIDENT", "REPORT", "VIEW"));
                permissions.add(buildPermission("INCIDENT_REPORT_CREATE", "Buat Laporan Insiden", "INCIDENT", "REPORT", "CREATE"));
                permissions.add(buildPermission("INCIDENT_REPORT_MANAGE", "Kelola Laporan Insiden", "INCIDENT", "REPORT", "MANAGE"));
                permissions.add(buildPermission("INCIDENT_INVESTIGATION_VIEW", "Lihat Investigasi Insiden", "INCIDENT", "INVESTIGATION", "VIEW"));
                permissions.add(buildPermission("INCIDENT_INVESTIGATION_MANAGE", "Kelola Investigasi Insiden", "INCIDENT", "INVESTIGATION", "MANAGE"));
                permissions.add(buildPermission("INCIDENT_FOLLOWUP_VIEW", "Lihat Tindak Lanjut Insiden", "INCIDENT", "FOLLOWUP", "VIEW"));
                permissions.add(buildPermission("INCIDENT_FOLLOWUP_MANAGE", "Kelola Tindak Lanjut Insiden", "INCIDENT", "FOLLOWUP", "MANAGE"));
                // Satu permission untuk manager departemen tujuan: akses halaman
                // Tugas Insiden sekaligus meng-assign tugas ke member departemennya.
                permissions.add(buildPermission("INCIDENT_FOLLOWUP_ASSIGN", "Tugas Insiden & Penugasan ke Member", "INCIDENT", "FOLLOWUP", "ASSIGN"));

                permissions.add(buildPermission("SAFETY_OBSERVATION_VIEW", "Lihat Observasi Keselamatan", "SAFETY", "OBSERVATION", "VIEW"));
                permissions.add(buildPermission("SAFETY_OBSERVATION_CREATE", "Buat Observasi Keselamatan", "SAFETY", "OBSERVATION", "CREATE"));
                permissions.add(buildPermission("SAFETY_OBSERVATION_MANAGE", "Kelola Observasi Keselamatan", "SAFETY", "OBSERVATION", "MANAGE"));
                permissions.add(buildPermission("SAFETY_AUDIT_VIEW", "Lihat Audit Keselamatan", "SAFETY", "AUDIT", "VIEW"));
                permissions.add(buildPermission("SAFETY_AUDIT_MANAGE", "Kelola Audit Keselamatan", "SAFETY", "AUDIT", "MANAGE"));

                permissions.add(buildPermission("RISK_ASSESSMENT_VIEW", "Lihat Penilaian Risiko", "RISK", "ASSESSMENT", "VIEW"));
                permissions.add(buildPermission("RISK_ASSESSMENT_MANAGE", "Kelola Penilaian Risiko", "RISK", "ASSESSMENT", "MANAGE"));
                permissions.add(buildPermission("RISK_REGISTER_VIEW", "Lihat Register Risiko", "RISK", "REGISTER", "VIEW"));
                permissions.add(buildPermission("RISK_REGISTER_MANAGE", "Kelola Register Risiko", "RISK", "REGISTER", "MANAGE"));
                permissions.add(buildPermission("RISK_MATRIX_VIEW", "Lihat Matriks Risiko", "RISK", "MATRIX", "VIEW"));

                permissions.add(buildPermission("LAB_REPORT_VIEW", "Lihat Laporan Laboratorium", "LIMS", "REPORT", "VIEW"));
                permissions.add(buildPermission("LAB_REPORT_CREATE", "Buat Laporan Laboratorium", "LIMS", "REPORT", "CREATE"));
                permissions.add(buildPermission("LAB_REPORT_MANAGE", "Kelola Laporan Laboratorium", "LIMS", "REPORT", "MANAGE"));
                permissions.add(buildPermission("LAB_ANALYSIS_VIEW", "Lihat Detail Analisis", "LIMS", "ANALYSIS", "VIEW"));
                permissions.add(buildPermission("LAB_ANALYSIS_MANAGE", "Kelola Detail Analisis", "LIMS", "ANALYSIS", "MANAGE"));
                permissions.add(buildPermission("LAB_REPORT_REVIEW", "Review Laporan Laboratorium", "LIMS", "APPROVAL", "APPROVE"));
                permissions.add(buildPermission("LAB_REPORT_APPROVE", "Approve Laporan Laboratorium", "LIMS", "APPROVAL", "APPROVE"));
                permissions.add(buildPermission("LAB_REPORT_REJECT", "Reject Laporan Laboratorium", "LIMS", "APPROVAL", "MANAGE"));

                permissions.add(buildPermission("DAILY_REPORT_VIEW", "Lihat Laporan Harian", "LIMS", "DAILY", "VIEW"));
                permissions.add(buildPermission("DAILY_REPORT_CREATE", "Buat Laporan Harian", "LIMS", "DAILY", "CREATE"));
                permissions.add(buildPermission("DAILY_REPORT_MANAGE", "Kelola Laporan Harian", "LIMS", "DAILY", "MANAGE"));
                permissions.add(buildPermission("DAILY_CALENDAR_VIEW", "Lihat Kalender Harian", "LIMS", "CALENDAR", "VIEW"));

                permissions.add(buildPermission("LAB_PARAMETER_VIEW", "Lihat Parameter Uji", "LIMS", "PARAMETER", "VIEW"));
                permissions.add(buildPermission("LAB_PARAMETER_MANAGE", "Kelola Parameter Uji", "LIMS", "PARAMETER", "MANAGE"));
                permissions.add(buildPermission("LAB_TEST_TYPE_VIEW", "Lihat Tipe Pengujian", "LIMS", "TEST_TYPE", "VIEW"));
                permissions.add(buildPermission("LAB_TEST_TYPE_MANAGE", "Kelola Tipe Pengujian", "LIMS", "TEST_TYPE", "MANAGE"));
                permissions.add(buildPermission("LAB_METHOD_VIEW", "Lihat Metode", "LIMS", "METHOD", "VIEW"));
                permissions.add(buildPermission("LAB_METHOD_MANAGE", "Kelola Metode", "LIMS", "METHOD", "MANAGE"));
                permissions.add(buildPermission("LAB_STANDARD_VIEW", "Lihat Standar", "LIMS", "STANDARD", "VIEW"));
                permissions.add(buildPermission("LAB_STANDARD_MANAGE", "Kelola Standar", "LIMS", "STANDARD", "MANAGE"));
                permissions.add(buildPermission("LAB_UNIT_VIEW", "Lihat Satuan", "LIMS", "UNIT", "VIEW"));
                permissions.add(buildPermission("LAB_UNIT_MANAGE", "Kelola Satuan", "LIMS", "UNIT", "MANAGE"));

                permissions.add(buildPermission("LAB_RESULT_VIEW", "Lihat Hasil Pengujian", "LIMS", "RESULT", "VIEW"));
                permissions.add(buildPermission("LAB_RESULT_MANAGE", "Kelola Hasil Pengujian", "LIMS", "RESULT", "MANAGE"));
                permissions.add(buildPermission("LAB_DEPT_VIEW", "Lihat Departemen Lab", "LIMS", "LAB_DEPT", "VIEW"));
                permissions.add(buildPermission("LAB_DEPT_MANAGE", "Kelola Departemen Lab", "LIMS", "LAB_DEPT", "MANAGE"));

                permissions.add(buildPermission("LAB_COMPOUND_VIEW", "Lihat Senyawa Kimia", "LIMS", "COMPOUND", "VIEW"));
                permissions.add(buildPermission("LAB_COMPOUND_MANAGE", "Kelola Senyawa Kimia", "LIMS", "COMPOUND", "MANAGE"));

                permissions.add(buildPermission("DEPARTMENT_VIEW", "Lihat Departemen", "ORGANISASI", "DEPARTMENT", "VIEW"));
                permissions.add(buildPermission("DEPARTMENT_MANAGE", "Kelola Departemen", "ORGANISASI", "DEPARTMENT", "MANAGE"));

                permissions.add(buildPermission("USER_VIEW", "Lihat User", "SYSTEM", "USER", "VIEW"));
                permissions.add(buildPermission("USER_MANAGE", "Kelola User", "SYSTEM", "USER", "MANAGE"));
                permissions.add(buildPermission("ROLE_VIEW", "Lihat Role", "SYSTEM", "ROLE", "VIEW"));
                permissions.add(buildPermission("ROLE_MANAGE", "Kelola Role", "SYSTEM", "ROLE", "MANAGE"));
                permissions.add(buildPermission("PERMISSION_VIEW", "Lihat Permission", "SYSTEM", "PERMISSION", "VIEW"));
                permissions.add(buildPermission("PERMISSION_MANAGE", "Kelola Permission", "SYSTEM", "PERMISSION", "MANAGE"));
                permissions.add(buildPermission("SETTINGS_VIEW", "Lihat Pengaturan", "SYSTEM", "SETTINGS", "VIEW"));
                permissions.add(buildPermission("SETTINGS_MANAGE", "Kelola Pengaturan", "SYSTEM", "SETTINGS", "MANAGE"));

                return permissions;
        }

        private AuthPermission buildPermission(String code, String name, String module, String subModule,
                        String action) {
                return AuthPermission.builder()
                                .code(code)
                                .name(name)
                                .title(name)
                                .module(module)
                                .subModule(subModule)
                                .action(action)
                                .isActive(true)
                                .createdBy("SYSTEM")
                                .build();
        }

        private List<AuthRole> createDefaultRoles() {
                List<AuthRole> roles = new ArrayList<>();

                roles.add(AuthRole.builder().code("SUPER_ADMIN").name("Super Administrator")
                                .description("Akses penuh ke semua modul dan fitur sistem")
                                .isSystemRole(true).isActive(true).priority(100).createdBy("SYSTEM").build());

                roles.add(AuthRole.builder().code("ADMIN_IT").name("Administrator IT")
                                .description("Mengelola sistem, user, dan konfigurasi")
                                .isSystemRole(true).isActive(true).priority(90).createdBy("SYSTEM").build());

                roles.add(AuthRole.builder().code("DIREKTUR").name("Direktur")
                                .description("Akses dashboard dan laporan eksekutif")
                                .isSystemRole(false).isActive(true).priority(85).createdBy("SYSTEM").build());

                roles.add(AuthRole.builder().code("SAFETY_OFFICER").name("Safety Officer")
                                .description("Mengelola keselamatan, observasi, dan audit")
                                .isSystemRole(false).isActive(true).priority(75).createdBy("SYSTEM").build());

                roles.add(AuthRole.builder().code("RISK_MANAGER").name("Risk Manager")
                                .description("Mengelola penilaian risiko dan register risiko")
                                .isSystemRole(false).isActive(true).priority(75).createdBy("SYSTEM").build());

                roles.add(AuthRole.builder().code("INCIDENT_REPORTER").name("Pelapor Insiden")
                                .description("Membuat dan melaporkan insiden")
                                .isSystemRole(false).isActive(true).priority(50).createdBy("SYSTEM").build());

                roles.add(AuthRole.builder().code("INVESTIGATOR").name("Investigator")
                                .description("Investigasi dan analisis insiden")
                                .isSystemRole(false).isActive(true).priority(65).createdBy("SYSTEM").build());

                roles.add(AuthRole.builder().code("DEPARTMENT_HEAD").name("Kepala Departemen")
                                .description("Approval dan monitoring insiden departemen")
                                .isSystemRole(false).isActive(true).priority(70).createdBy("SYSTEM").build());

                roles.add(AuthRole.builder().code("MANAGER_LAB").name("Manajer Laboratorium")
                                .description("Mengelola konfigurasi lab, parameter uji, dan master data laboratorium")
                                .isSystemRole(false).isActive(true).priority(70).createdBy("SYSTEM").build());

                roles.add(AuthRole.builder().code("STAFF_LAB").name("Staff Laboratorium")
                                .description("Membuat laporan laboratorium dan laporan harian")
                                .isSystemRole(false).isActive(true).priority(50).createdBy("SYSTEM").build());

                roles.add(AuthRole.builder().code("QUALITY_MANAGER").name("Manajer Kualitas")
                                .description("Review dan approval laporan laboratorium, kelola kualitas")
                                .isSystemRole(false).isActive(true).priority(70).createdBy("SYSTEM").build());

                roles.add(AuthRole.builder().code("QUALITY_STAFF").name("Staff Kualitas")
                                .description("Monitoring hasil pengujian")
                                .isSystemRole(false).isActive(true).priority(50).createdBy("SYSTEM").build());

                roles.add(AuthRole.builder().code("KEPALA_LAB").name("Kepala Laboratorium")
                                .description("Mengelola operasional lab dan approval laporan")
                                .isSystemRole(false).isActive(true).priority(75).createdBy("SYSTEM").build());

                roles.add(AuthRole.builder().code("ANALIS_LAB").name("Analis Laboratorium")
                                .description("Melakukan analisis pengujian dan membuat laporan harian")
                                .isSystemRole(false).isActive(true).priority(50).createdBy("SYSTEM").build());

                roles.add(AuthRole.builder().code("REVIEWER_LAB").name("Reviewer Laboratorium")
                                .description("Review dan validasi hasil pengujian laboratorium")
                                .isSystemRole(false).isActive(true).priority(60).createdBy("SYSTEM").build());

                roles.add(AuthRole.builder().code("APPROVER_LAB").name("Approver Laboratorium")
                                .description("Approval akhir laporan laboratorium")
                                .isSystemRole(false).isActive(true).priority(75).createdBy("SYSTEM").build());

                return roles;
        }

        private void assignDefaultPermissionsToRoles() {
                Map<String, AuthRole> roleMap = authRoleRepository.findAll().stream()
                                .collect(Collectors.toMap(AuthRole::getCode, r -> r));
                Map<String, AuthPermission> permMap = authPermissionRepository.findAll().stream()
                                .collect(Collectors.toMap(AuthPermission::getCode, p -> p));

                List<AuthRolePermission> rolePermissions = new ArrayList<>();

                AuthRole superAdmin = roleMap.get("SUPER_ADMIN");
                for (AuthPermission perm : permMap.values()) {
                        rolePermissions.add(buildRolePermission(superAdmin.getId(), perm.getId(), true, true, true, true, true, true));
                }

                AuthRole adminIt = roleMap.get("ADMIN_IT");
                assignPermissions(rolePermissions, adminIt, permMap,
                                Arrays.asList("USER_VIEW", "USER_MANAGE", "ROLE_VIEW", "ROLE_MANAGE",
                                                "PERMISSION_VIEW", "PERMISSION_MANAGE", "SETTINGS_VIEW",
                                                "SETTINGS_MANAGE", "DASHBOARD_VIEW"),
                                true, true, true, true, false, true);

                AuthRole direktur = roleMap.get("DIREKTUR");
                for (AuthPermission perm : permMap.values()) {
                        rolePermissions.add(buildRolePermission(direktur.getId(), perm.getId(), false, true, false, false, true, true));
                }

                AuthRole safetyOfficer = roleMap.get("SAFETY_OFFICER");
                if (safetyOfficer != null) {
                        assignPermissions(rolePermissions, safetyOfficer, permMap,
                                        Arrays.asList("DASHBOARD_VIEW",
                                                        "INCIDENT_REPORT_VIEW", "INCIDENT_REPORT_CREATE", "INCIDENT_REPORT_MANAGE",
                                                        "INCIDENT_INVESTIGATION_VIEW", "INCIDENT_INVESTIGATION_MANAGE",
                                                        "INCIDENT_FOLLOWUP_VIEW", "INCIDENT_FOLLOWUP_MANAGE",
                                                        "SAFETY_OBSERVATION_VIEW", "SAFETY_OBSERVATION_CREATE", "SAFETY_OBSERVATION_MANAGE",
                                                        "SAFETY_AUDIT_VIEW", "SAFETY_AUDIT_MANAGE",
                                                        "RISK_ASSESSMENT_VIEW", "RISK_REGISTER_VIEW", "RISK_MATRIX_VIEW"),
                                        true, true, true, true, true, true);
                }

                AuthRole riskManager = roleMap.get("RISK_MANAGER");
                if (riskManager != null) {
                        assignPermissions(rolePermissions, riskManager, permMap,
                                        Arrays.asList("DASHBOARD_VIEW",
                                                        "RISK_ASSESSMENT_VIEW", "RISK_ASSESSMENT_MANAGE",
                                                        "RISK_REGISTER_VIEW", "RISK_REGISTER_MANAGE", "RISK_MATRIX_VIEW",
                                                        "INCIDENT_REPORT_VIEW", "INCIDENT_INVESTIGATION_VIEW",
                                                        "SAFETY_OBSERVATION_VIEW", "SAFETY_AUDIT_VIEW"),
                                        true, true, true, true, true, true);
                }

                AuthRole incidentReporter = roleMap.get("INCIDENT_REPORTER");
                if (incidentReporter != null) {
                        assignPermissions(rolePermissions, incidentReporter, permMap,
                                        Arrays.asList("DASHBOARD_VIEW",
                                                        "INCIDENT_REPORT_VIEW", "INCIDENT_REPORT_CREATE",
                                                        "INCIDENT_FOLLOWUP_VIEW",
                                                        "SAFETY_OBSERVATION_VIEW", "SAFETY_OBSERVATION_CREATE"),
                                        true, true, false, false, false, false);
                }

                AuthRole investigator = roleMap.get("INVESTIGATOR");
                if (investigator != null) {
                        assignPermissions(rolePermissions, investigator, permMap,
                                        Arrays.asList("DASHBOARD_VIEW",
                                                        "INCIDENT_REPORT_VIEW", "INCIDENT_REPORT_MANAGE",
                                                        "INCIDENT_INVESTIGATION_VIEW", "INCIDENT_INVESTIGATION_MANAGE",
                                                        "INCIDENT_FOLLOWUP_VIEW", "INCIDENT_FOLLOWUP_MANAGE",
                                                        "SAFETY_OBSERVATION_VIEW", "RISK_ASSESSMENT_VIEW"),
                                        true, true, true, false, false, true);
                }

                AuthRole departmentHead = roleMap.get("DEPARTMENT_HEAD");
                if (departmentHead != null) {
                        assignPermissions(rolePermissions, departmentHead, permMap,
                                        Arrays.asList("DASHBOARD_VIEW",
                                                        "INCIDENT_REPORT_VIEW", "INCIDENT_REPORT_MANAGE",
                                                        "INCIDENT_INVESTIGATION_VIEW",
                                                        "INCIDENT_FOLLOWUP_VIEW", "INCIDENT_FOLLOWUP_MANAGE",
                                                        "INCIDENT_FOLLOWUP_ASSIGN",
                                                        "SAFETY_OBSERVATION_VIEW",
                                                        "RISK_ASSESSMENT_VIEW", "RISK_REGISTER_VIEW", "RISK_MATRIX_VIEW",
                                                        "DEPARTMENT_VIEW"),
                                        false, true, true, false, true, true);
                }

                AuthRole mgrLab = roleMap.get("MANAGER_LAB");
                if (mgrLab != null) {
                        assignPermissions(rolePermissions, mgrLab, permMap,
                                        Arrays.asList("DASHBOARD_VIEW",
                                                        "LAB_PARAMETER_VIEW", "LAB_PARAMETER_MANAGE",
                                                        "LAB_TEST_TYPE_VIEW", "LAB_TEST_TYPE_MANAGE",
                                                        "LAB_METHOD_VIEW", "LAB_METHOD_MANAGE",
                                                        "LAB_STANDARD_VIEW", "LAB_STANDARD_MANAGE",
                                                        "LAB_UNIT_VIEW", "LAB_UNIT_MANAGE",
                                                        "LAB_RESULT_VIEW", "LAB_RESULT_MANAGE",
                                                        "LAB_DEPT_VIEW", "LAB_DEPT_MANAGE",
                                                        "LAB_COMPOUND_VIEW", "LAB_COMPOUND_MANAGE",
                                                        "LAB_REPORT_VIEW"),
                                        true, true, true, true, true, true);
                }

                AuthRole staffLab = roleMap.get("STAFF_LAB");
                if (staffLab != null) {
                        assignPermissions(rolePermissions, staffLab, permMap,
                                        Arrays.asList("DASHBOARD_VIEW",
                                                        "LAB_REPORT_VIEW", "LAB_REPORT_CREATE",
                                                        "LAB_ANALYSIS_VIEW", "LAB_ANALYSIS_MANAGE",
                                                        "DAILY_REPORT_VIEW", "DAILY_REPORT_CREATE", "DAILY_CALENDAR_VIEW",
                                                        "LAB_PARAMETER_VIEW", "LAB_TEST_TYPE_VIEW",
                                                        "LAB_METHOD_VIEW", "LAB_STANDARD_VIEW",
                                                        "LAB_UNIT_VIEW", "LAB_RESULT_VIEW"),
                                        true, true, true, false, false, false);
                }

                AuthRole qualityMgr = roleMap.get("QUALITY_MANAGER");
                if (qualityMgr != null) {
                        assignPermissions(rolePermissions, qualityMgr, permMap,
                                        Arrays.asList("DASHBOARD_VIEW",
                                                        "LAB_REPORT_VIEW", "LAB_REPORT_MANAGE",
                                                        "LAB_REPORT_REVIEW", "LAB_REPORT_APPROVE",
                                                        "LAB_ANALYSIS_VIEW",
                                                        "LAB_RESULT_VIEW", "LAB_RESULT_MANAGE",
                                                        "LAB_DEPT_VIEW", "LAB_DEPT_MANAGE"),
                                        true, true, true, true, true, true);
                }

                AuthRole qualityStaff = roleMap.get("QUALITY_STAFF");
                if (qualityStaff != null) {
                        assignPermissions(rolePermissions, qualityStaff, permMap,
                                        Arrays.asList("DASHBOARD_VIEW",
                                                        "LAB_REPORT_VIEW", "LAB_ANALYSIS_VIEW",
                                                        "LAB_RESULT_VIEW", "LAB_DEPT_VIEW"),
                                        false, true, true, false, false, false);
                }

                AuthRole kepalaLab = roleMap.get("KEPALA_LAB");
                if (kepalaLab != null) {
                        assignPermissions(rolePermissions, kepalaLab, permMap,
                                        Arrays.asList("DASHBOARD_VIEW",
                                                        "LAB_REPORT_VIEW", "LAB_REPORT_CREATE",
                                                        "LAB_REPORT_MANAGE", "LAB_REPORT_REVIEW",
                                                        "LAB_ANALYSIS_VIEW", "LAB_ANALYSIS_MANAGE",
                                                        "DAILY_REPORT_VIEW", "DAILY_REPORT_CREATE",
                                                        "DAILY_REPORT_MANAGE", "DAILY_CALENDAR_VIEW",
                                                        "LAB_PARAMETER_VIEW", "LAB_PARAMETER_MANAGE",
                                                        "LAB_TEST_TYPE_VIEW", "LAB_TEST_TYPE_MANAGE",
                                                        "LAB_METHOD_VIEW", "LAB_METHOD_MANAGE",
                                                        "LAB_STANDARD_VIEW", "LAB_STANDARD_MANAGE",
                                                        "LAB_UNIT_VIEW", "LAB_UNIT_MANAGE",
                                                        "LAB_RESULT_VIEW", "LAB_RESULT_MANAGE",
                                                        "LAB_COMPOUND_VIEW", "LAB_COMPOUND_MANAGE"),
                                        true, true, true, true, true, true);
                }

                AuthRole analisLab = roleMap.get("ANALIS_LAB");
                if (analisLab != null) {
                        assignPermissions(rolePermissions, analisLab, permMap,
                                        Arrays.asList("DASHBOARD_VIEW",
                                                        "LAB_REPORT_VIEW", "LAB_REPORT_CREATE",
                                                        "LAB_ANALYSIS_VIEW", "LAB_ANALYSIS_MANAGE",
                                                        "DAILY_REPORT_VIEW", "DAILY_REPORT_CREATE", "DAILY_CALENDAR_VIEW",
                                                        "LAB_PARAMETER_VIEW", "LAB_TEST_TYPE_VIEW",
                                                        "LAB_METHOD_VIEW", "LAB_RESULT_VIEW"),
                                        true, true, true, false, false, false);
                }

                AuthRole reviewerLab = roleMap.get("REVIEWER_LAB");
                if (reviewerLab != null) {
                        assignPermissions(rolePermissions, reviewerLab, permMap,
                                        Arrays.asList("DASHBOARD_VIEW",
                                                        "LAB_REPORT_VIEW", "LAB_REPORT_REVIEW",
                                                        "LAB_ANALYSIS_VIEW", "LAB_RESULT_VIEW"),
                                        false, true, true, false, true, true);
                }

                AuthRole approverLab = roleMap.get("APPROVER_LAB");
                if (approverLab != null) {
                        assignPermissions(rolePermissions, approverLab, permMap,
                                        Arrays.asList("DASHBOARD_VIEW",
                                                        "LAB_REPORT_VIEW", "LAB_REPORT_APPROVE",
                                                        "LAB_ANALYSIS_VIEW",
                                                        "LAB_RESULT_VIEW", "LAB_RESULT_MANAGE"),
                                        true, true, true, false, true, true);
                }

                // Deduplicate by (roleId, permissionId) — last entry wins
                Map<String, AuthRolePermission> deduped = new LinkedHashMap<>();
                for (AuthRolePermission rp : rolePermissions) {
                        deduped.put(rp.getRoleId() + "-" + rp.getPermissionId(), rp);
                }
                authRolePermissionRepository.saveAll(deduped.values());
        }

        private void assignPermissions(List<AuthRolePermission> list, AuthRole role,
                        Map<String, AuthPermission> permMap, List<String> permCodes,
                        boolean create, boolean read, boolean update, boolean delete,
                        boolean approve, boolean export) {
                for (String code : permCodes) {
                        AuthPermission perm = permMap.get(code);
                        if (perm != null) {
                                list.add(buildRolePermission(role.getId(), perm.getId(), create, read, update, delete, approve, export));
                        }
                }
        }

        private AuthRolePermission buildRolePermission(Long roleId, Long permId,
                        boolean create, boolean read, boolean update,
                        boolean delete, boolean approve, boolean export) {
                return AuthRolePermission.builder()
                                .roleId(roleId)
                                .permissionId(permId)
                                .canCreate(create)
                                .canRead(read)
                                .canUpdate(update)
                                .canDelete(delete)
                                .canApprove(approve)
                                .canExport(export)
                                .createdBy("SYSTEM")
                                .build();
        }

        // =====================================================
        // HELPER METHODS
        // =====================================================

        private Map<String, Object> roleToMap(AuthRole role) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", role.getId());
                map.put("code", role.getCode());
                map.put("name", role.getName());
                map.put("description", role.getDescription());
                map.put("departmentId", role.getDepartmentId());
                map.put("isSystemRole", role.getIsSystemRole());
                map.put("isActive", role.getIsActive());
                map.put("priority", role.getPriority());
                map.put("createdAt", role.getCreatedAt());
                map.put("createdBy", role.getCreatedBy());
                return map;
        }

        private Map<String, Object> permissionToMap(AuthPermission perm) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", perm.getId());
                map.put("code", perm.getCode());
                map.put("name", perm.getName());
                map.put("description", perm.getDescription());
                map.put("module", perm.getModule());
                map.put("subModule", perm.getSubModule());
                map.put("action", perm.getAction());
                map.put("isActive", perm.getIsActive());
                return map;
        }

        private Map<String, Object> userRoleToMap(AuthUserRole ur) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", ur.getId());
                map.put("userId", ur.getUserId());
                map.put("roleId", ur.getRoleId());
                map.put("isPrimary", ur.getIsPrimary());
                map.put("isActive", ur.getIsActive());
                map.put("assignedAt", ur.getAssignedAt());
                map.put("assignedBy", ur.getAssignedBy());
                // See the identical guard in userToMap(): ur.getRole() != null does not catch a
                // role_id whose row was deleted — that only throws once the lazy proxy is touched.
                boolean roleLoaded = false;
                try {
                        if (ur.getRole() != null) {
                                map.put("roleName", ur.getRole().getName());
                                map.put("roleCode", ur.getRole().getCode());
                                map.put("role", roleToMap(ur.getRole()));
                                roleLoaded = true;
                        }
                } catch (jakarta.persistence.EntityNotFoundException ignored) {
                        // fall through to the defensive lookup below
                }
                if (!roleLoaded) {
                        authRoleRepository.findById(ur.getRoleId()).ifPresent(role -> {
                                map.put("roleName", role.getName());
                                map.put("roleCode", role.getCode());
                        });
                }
                return map;
        }

        private Map<String, Object> rolePermissionToMap(AuthRolePermission rp) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", rp.getId());
                map.put("roleId", rp.getRoleId());
                map.put("permissionId", rp.getPermissionId());
                map.put("canCreate", rp.getCanCreate());
                map.put("canRead", rp.getCanRead());
                map.put("canUpdate", rp.getCanUpdate());
                map.put("canDelete", rp.getCanDelete());
                map.put("canApprove", rp.getCanApprove());
                map.put("canExport", rp.getCanExport());
                if (rp.getPermission() != null) {
                        map.put("permission", permissionToMap(rp.getPermission()));
                }
                return map;
        }

        private Long getLongValue(Map<String, Object> map, String key) {
                Object value = map.get(key);
                if (value == null) return null;
                if (value instanceof Number) return ((Number) value).longValue();
                try { return Long.parseLong(value.toString()); } catch (NumberFormatException e) { return null; }
        }

        private Integer getIntegerValue(Map<String, Object> map, String key, Integer defaultValue) {
                Object value = map.get(key);
                if (value == null) return defaultValue;
                if (value instanceof Number) return ((Number) value).intValue();
                try { return Integer.parseInt(value.toString()); } catch (NumberFormatException e) { return defaultValue; }
        }

        private Boolean getBooleanValue(Map<String, Object> map, String key, Boolean defaultValue) {
                Object value = map.get(key);
                if (value == null) return defaultValue;
                if (value instanceof Boolean) return (Boolean) value;
                return Boolean.parseBoolean(value.toString());
        }
}
