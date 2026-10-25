package id.neravoxa.erp.core;

import id.neravoxa.erp.entity.Account;
import id.neravoxa.erp.support.ErpIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regresi: JPQL {@code like} memakai pola {@link ErpQuery#contains} (escape backslash) harus cocok
 * untuk kata kunci berisi {@code _}/{@code %} dan tidak memperlakukannya sebagai wildcard
 * (lihat {@link ErpPostgreSQLDialect}).
 */
class ErpLikeEscapeTest extends ErpIntegrationTest {

    @PersistenceContext(unitName = "erp")
    EntityManager em;

    @Autowired
    @Qualifier("erpJdbcTemplate")
    JdbcTemplate jdbc;

    private List<String> namesContaining(String term) {
        return em.createQuery("select a.name from Account a where lower(a.name) like :q order by a.name", String.class)
                .setParameter("q", ErpQuery.contains(term))
                .getResultList();
    }

    @Test
    void underscore_and_percent_match_literally_in_jpql() {
        jdbc.update("""
                INSERT INTO accounts (id, account_number, name, account_type, root_type, is_group, created_at, updated_at)
                VALUES ('like-test-1', '9.9.91', 'Uji PT_ABC 50% Like', 'EXPENSE', 'EXPENSE', false, now(), now()),
                       ('like-test-2', '9.9.92', 'Uji PTXABC 50X Like', 'EXPENSE', 'EXPENSE', false, now(), now())
                ON CONFLICT (id) DO NOTHING
                """);
        try {
            assertEquals(List.of("Uji PT_ABC 50% Like"), namesContaining("pt_abc"));
            assertEquals(List.of("Uji PT_ABC 50% Like"), namesContaining("50%"));
            List<String> both = namesContaining("uji pt");
            assertTrue(both.containsAll(List.of("Uji PT_ABC 50% Like", "Uji PTXABC 50X Like")), both.toString());
            assertEquals(List.of(), namesContaining("pt\\abc"));
        } finally {
            // DB dipakai bersama semua tes ERP: jangan tinggalkan akun uji di bagan akun.
            jdbc.update("DELETE FROM accounts WHERE id IN ('like-test-1', 'like-test-2')");
        }
    }

    @Test
    void entity_class_is_mapped() {
        assertTrue(em.getMetamodel().getEntities().stream().anyMatch(e -> e.getJavaType() == Account.class));
    }
}
