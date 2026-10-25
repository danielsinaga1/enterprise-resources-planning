package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.accounting.ErpSystemAccountService;
import id.neravoxa.erp.core.accounting.SystemAccountRole;
import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.entity.SystemAccountMapping;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pemetaan akun sistem (role → akun) — port {@code routes/systemAccounts.ts}. Cache
 * {@link ErpSystemAccountService} di-invalidate setelah COMMIT setiap perubahan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpSystemAccountMappingService {

    private final ErpSystemAccountService systemAccounts;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Satu pemetaan yang diminta (hasil parse body). */
    record MappingInput(String role, String accountId, int sortOrder) {
    }

    /** GET /system-accounts — {@code { mappings: { ROLE: [...] }, roles: [...] }}. */
    @ErpTx(readOnly = true)
    public ObjectNode overview() {
        ObjectNode grouped = ErpJson.object();
        for (SystemAccountMapping m : em.createQuery("select m from SystemAccountMapping m join fetch m.account "
                + "order by m.role asc, m.sortOrder asc", SystemAccountMapping.class).getResultList()) {
            ArrayNode list = grouped.has(m.getRole()) ? (ArrayNode) grouped.get(m.getRole()) : grouped.putArray(m.getRole());
            ObjectNode e = list.addObject();
            e.put("id", m.getId());
            e.put("accountId", m.getAccount().getId());
            e.put("accountNumber", m.getAccount().getAccountNumber());
            e.put("accountName", m.getAccount().getName());
            e.set("accountType", AccountingJson.value(m.getAccount().getAccountType()));
            e.put("sortOrder", m.getSortOrder());
        }
        ArrayNode roles = ErpJson.arrayNode();
        for (SystemAccountRole r : SystemAccountRole.values()) {
            ObjectNode o = roles.addObject();
            o.put("key", r.name());
            o.put("label", r.label());
            o.put("description", r.description());
            o.put("multiAccount", r.multiAccount());
            o.put("required", r.required());
            o.put("expectedRootType", r.expectedRootType().name());
        }
        ObjectNode out = ErpJson.object();
        out.set("mappings", grouped);
        out.set("roles", roles);
        return out;
    }

    /**
     * PUT /system-accounts — ganti pemetaan untuk role yang dikirim ({@code mappings}) dan lepas total
     * role di {@code clearRoles} (tidak boleh role wajib). Akun harus ada & aktif; role satu-akun
     * tidak boleh menerima lebih dari satu akun.
     */
    @ErpTx
    public ObjectNode update(JsonNode body) {
        List<MappingInput> inputs = new ArrayList<>();
        List<String> clearRoles = new ArrayList<>();
        parse(body, inputs, clearRoles);

        for (MappingInput m : inputs) {
            if (!SystemAccountRole.isRole(m.role())) {
                throw ErpException.business("Role \"" + m.role() + "\" tidak valid.");
            }
        }
        // clearRoles: harus valid & tidak wajib.
        for (String role : clearRoles) {
            if (!SystemAccountRole.isRole(role)) {
                throw ErpException.business("Role \"" + role + "\" tidak valid.");
            }
            SystemAccountRole meta = SystemAccountRole.valueOf(role);
            if (meta.required()) {
                throw ErpException.business("Role \"" + meta.label()
                        + "\" wajib dan tidak dapat dilepas. Ganti ke akun lain, bukan dikosongkan.");
            }
        }

        // Semua akun harus ada dan aktif.
        Set<String> accountIds = new LinkedHashSet<>();
        inputs.forEach(m -> accountIds.add(m.accountId()));
        Map<String, Account> accounts = new HashMap<>();
        if (!accountIds.isEmpty()) {
            for (Account a : em.createQuery("select a from Account a where a.id in :ids", Account.class)
                    .setParameter("ids", accountIds).getResultList()) {
                accounts.put(a.getId(), a);
            }
        }
        for (MappingInput m : inputs) {
            Account a = accounts.get(m.accountId());
            if (a == null) {
                throw ErpException.business("Akun dengan ID \"" + m.accountId() + "\" tidak ditemukan.");
            }
            if (!Boolean.TRUE.equals(a.getIsActive())) {
                throw ErpException.business("Akun \"" + a.getAccountNumber() + " - " + a.getName() + "\" tidak aktif.");
            }
        }

        // Role satu-akun tidak boleh punya beberapa entri.
        Map<String, Integer> roleCounts = new LinkedHashMap<>();
        inputs.forEach(m -> roleCounts.merge(m.role(), 1, Integer::sum));
        for (Map.Entry<String, Integer> e : roleCounts.entrySet()) {
            SystemAccountRole meta = SystemAccountRole.valueOf(e.getKey());
            if (!meta.multiAccount() && e.getValue() > 1) {
                throw ErpException.business("Role \"" + meta.label() + "\" hanya boleh memiliki 1 akun, tapi diberikan "
                        + e.getValue() + ".");
            }
        }

        // Role yang diperbarui (dipetakan + dilepas).
        Set<String> rolesToUpdate = new LinkedHashSet<>();
        inputs.forEach(m -> rolesToUpdate.add(m.role()));
        rolesToUpdate.addAll(clearRoles);

        em.createQuery("delete from SystemAccountMapping m where m.role in :roles")
                .setParameter("roles", rolesToUpdate)
                .executeUpdate();
        for (MappingInput m : inputs) {
            SystemAccountMapping row = new SystemAccountMapping();
            row.setRole(m.role());
            row.setAccountId(m.accountId());
            row.setSortOrder(m.sortOrder());
            em.persist(row);
        }
        AccountingDb.flush(em);
        invalidateCacheAfterCommit();
        // Entity baru masih ter-manage dengan relasi account (read-only) kosong — muat ulang dari DB.
        em.clear();

        ArrayNode updated = ErpJson.arrayNode();
        for (SystemAccountMapping m : em.createQuery("select m from SystemAccountMapping m join fetch m.account "
                        + "where m.role in :roles order by m.role asc, m.sortOrder asc", SystemAccountMapping.class)
                .setParameter("roles", rolesToUpdate).getResultList()) {
            ObjectNode o = updated.addObject();
            o.put("role", m.getRole());
            o.put("accountId", m.getAccount().getId());
            o.put("accountNumber", m.getAccount().getAccountNumber());
            o.put("accountName", m.getAccount().getName());
            o.put("sortOrder", m.getSortOrder());
        }
        log.info("Konfigurasi akun sistem diperbarui untuk role {}", rolesToUpdate);
        ObjectNode out = AccountingJson.message("Konfigurasi akun sistem diperbarui.");
        out.set("updated", updated);
        return out;
    }

    /** Cache pemetaan di-reset setelah COMMIT agar pembaca lain tidak memuat ulang data lama. */
    private void invalidateCacheAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    systemAccounts.invalidateCache();
                }
            });
        } else {
            systemAccounts.invalidateCache();
        }
    }

    /**
     * Port skema Zod {@code updateSchema}: mappings[] {role: string, accountId: uuid,
     * sortOrder: int ≥ 0 default 0} default [], clearRoles: string[] default [], dan refine
     * "minimal satu perubahan". Gagal → 400 {@code { error: 'Data tidak valid.', details }}.
     */
    private static void parse(JsonNode body, List<MappingInput> inputs, List<String> clearRoles) {
        ZodLite.Issues is = new ZodLite.Issues();
        if (body == null || !body.isObject()) {
            is.type(List.of(), ZodLite.invalidType("object", body));
        } else {
            JsonNode mappings = body.get("mappings");
            if (mappings != null) {
                if (!mappings.isArray()) {
                    is.type(List.of("mappings"), ZodLite.invalidType("array", mappings));
                } else {
                    for (int i = 0; i < mappings.size(); i++) {
                        MappingInput m = mapping(mappings.get(i), List.of("mappings", i), is);
                        if (m != null) {
                            inputs.add(m);
                        }
                    }
                }
            }
            JsonNode clear = body.get("clearRoles");
            if (clear != null) {
                if (!clear.isArray()) {
                    is.type(List.of("clearRoles"), ZodLite.invalidType("array", clear));
                } else {
                    for (int i = 0; i < clear.size(); i++) {
                        JsonNode r = clear.get(i);
                        if (!r.isTextual()) {
                            is.type(List.of("clearRoles", i), ZodLite.invalidType("string", r));
                        } else {
                            clearRoles.add(r.textValue());
                        }
                    }
                }
            }
            if (is.isEmpty() && inputs.isEmpty() && clearRoles.isEmpty()) {
                is.check(List.of(), "Tidak ada perubahan untuk disimpan.");
            }
        }
        if (!is.isEmpty()) {
            throw ErpException.business("Data tidak valid.").with("details", is.format());
        }
    }

    private static MappingInput mapping(JsonNode m, List<Object> path, ZodLite.Issues is) {
        if (m == null || !m.isObject()) {
            is.type(path, ZodLite.invalidType("object", m));
            return null;
        }
        int before = is.list.size();
        JsonNode role = m.get("role");
        if (role == null || !role.isTextual()) {
            is.type(ZodLite.path(path, "role"), ZodLite.invalidType("string", role));
        }
        JsonNode accountId = m.get("accountId");
        if (accountId == null || !accountId.isTextual()) {
            is.type(ZodLite.path(path, "accountId"), ZodLite.invalidType("string", accountId));
        } else if (!ZodLite.UUID.matcher(accountId.textValue()).matches()) {
            is.check(ZodLite.path(path, "accountId"), ZodLite.INVALID_UUID);
        }
        int sortOrder = 0;
        JsonNode so = m.get("sortOrder");
        if (so != null) {
            if (!so.isNumber()) {
                is.type(ZodLite.path(path, "sortOrder"), ZodLite.invalidType("number", so));
            } else {
                BigDecimal v = so.decimalValue();
                boolean integer = v.stripTrailingZeros().scale() <= 0;
                if (!integer || v.abs().compareTo(BigDecimal.valueOf(9007199254740991L)) > 0) {
                    is.type(ZodLite.path(path, "sortOrder"), "Invalid input: expected int, received number");
                } else if (v.signum() < 0) {
                    is.check(ZodLite.path(path, "sortOrder"), ZodLite.tooSmall(true, "0"));
                } else {
                    sortOrder = v.intValueExact();
                }
            }
        }
        if (is.list.size() > before) {
            return null;
        }
        return new MappingInput(role.textValue(), accountId.textValue(), sortOrder);
    }
}
