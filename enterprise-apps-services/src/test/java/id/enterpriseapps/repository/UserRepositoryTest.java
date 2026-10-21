package id.enterpriseapps.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import id.enterpriseapps.entity.UserEntity;

@DataJpaTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@DisplayName("UserRepository @DataJpaTest")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserRepository userRepository;

    private UserEntity activeAlice;
    private UserEntity activeBob;
    private UserEntity inactiveCharlie;

    @BeforeEach
    void setUp() {
        activeAlice = newUser("NPK001", "NPP-A", "Alice", "alice@enterpriseapps.local", (short) 1, null);
        activeBob = newUser("NPK002", "NPP-B", "Bob", "bob@enterpriseapps.local", (short) 1, activeAlice);
        inactiveCharlie = newUser("NPK003", "NPP-C", "Charlie", "charlie@enterpriseapps.local", (short) 0, null);

        em.persist(activeAlice);
        em.persist(activeBob);
        em.persist(inactiveCharlie);
        em.flush();

        // Re-link Bob to Alice's persisted id
        activeBob.setParentId(activeAlice.getId());
        em.merge(activeBob);
        em.flush();
        em.clear();
    }

    private UserEntity newUser(String npk, String npp, String name, String email, Short status, UserEntity parent) {
        UserEntity u = new UserEntity();
        u.setNpk(npk);
        u.setNpp(npp);
        u.setName(name);
        u.setEmail(email);
        u.setStatus(status);
        u.setPassword("$2a$10$hash");
        u.setAttemptLoginFailed(0);
        if (parent != null && parent.getId() != null) {
            u.setParentId(parent.getId());
        }
        return u;
    }

    @Nested
    @DisplayName("Finder by unique field")
    class FinderTests {

        @Test
        @DisplayName("findByEmail - user ditemukan")
        void findByEmail_Found() {
            Optional<UserEntity> found = userRepository.findByEmail("alice@enterpriseapps.local");

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("findByEmail - user tidak ada return empty")
        void findByEmail_NotFound() {
            Optional<UserEntity> found = userRepository.findByEmail("ghost@nowhere.local");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("findByNpk - cocok exact match")
        void findByNpk_Found() {
            Optional<UserEntity> found = userRepository.findByNpk("NPK002");

            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isEqualTo("bob@enterpriseapps.local");
        }

        @Test
        @DisplayName("findByNpp - cocok exact match")
        void findByNpp_Found() {
            Optional<UserEntity> found = userRepository.findByNpp("NPP-A");

            assertThat(found).isPresent();
            assertThat(found.get().getNpk()).isEqualTo("NPK001");
        }

        @Test
        @DisplayName("existsByEmail - true bila ada")
        void existsByEmail_True() {
            assertThat(userRepository.existsByEmail("alice@enterpriseapps.local")).isTrue();
        }

        @Test
        @DisplayName("existsByEmail - false bila tidak ada")
        void existsByEmail_False() {
            assertThat(userRepository.existsByEmail("ghost@nowhere.local")).isFalse();
        }
    }

    @Nested
    @DisplayName("Hierarchy queries")
    class HierarchyTests {

        @Test
        @DisplayName("findByParentId - return semua subordinate")
        void findByParentId_ReturnsChildren() {
            Long aliceId = userRepository.findByEmail("alice@enterpriseapps.local").orElseThrow().getId();

            List<UserEntity> subordinates = userRepository.findByParentId(aliceId);

            assertThat(subordinates).hasSize(1);
            assertThat(subordinates.get(0).getName()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("findPotentialSuperiors - exclude diri sendiri, hanya yang active")
        void findPotentialSuperiors_ExcludeSelfAndInactive() {
            Long bobId = userRepository.findByNpk("NPK002").orElseThrow().getId();

            List<UserEntity> candidates = userRepository.findPotentialSuperiors(bobId);

            assertThat(candidates)
                    .extracting(UserEntity::getName)
                    .containsExactly("Alice")
                    .doesNotContain("Bob", "Charlie"); // self + inactive excluded
        }
    }

    @Nested
    @DisplayName("Search query (JPQL with LIKE)")
    class SearchTests {

        @Test
        @DisplayName("searchUsers - match substring di name (case-insensitive)")
        void searchUsers_MatchName() {
            var page = userRepository.searchUsers("ali", org.springframework.data.domain.PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(UserEntity::getName).contains("Alice");
        }

        @Test
        @DisplayName("searchUsers - match substring di npk")
        void searchUsers_MatchNpk() {
            var page = userRepository.searchUsers("npk00", org.springframework.data.domain.PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSizeGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("searchUsers - no match return page kosong")
        void searchUsers_NoMatch() {
            var page = userRepository.searchUsers("zzzzz-nothing", org.springframework.data.domain.PageRequest.of(0, 10));

            assertThat(page.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudTests {

        @Test
        @DisplayName("save() persist user baru dan generate id")
        void save_GeneratesId() {
            UserEntity newUser = newUser("NPK999", "NPP-Z", "Zoe", "zoe@enterpriseapps.local", (short) 1, null);

            UserEntity saved = userRepository.save(newUser);

            assertThat(saved.getId()).isNotNull();
            assertThat(em.find(UserEntity.class, saved.getId())).isNotNull();
        }

        @Test
        @DisplayName("count() menghitung semua user yang persisted")
        void count_All() {
            long count = userRepository.count();

            assertThat(count).isEqualTo(3);
        }
    }
}
