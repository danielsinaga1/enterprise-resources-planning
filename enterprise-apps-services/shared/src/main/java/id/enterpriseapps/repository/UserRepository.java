package id.enterpriseapps.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import id.enterpriseapps.entity.UserEntity;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {
    Optional<UserEntity> findFirstByEmailOrderByIdAsc(String email);
    Optional<UserEntity> findFirstByNppOrderByIdAsc(String npp);
    Optional<UserEntity> findFirstByNpkOrderByIdAsc(String npk);
    boolean existsByEmail(String email);

    // Hierarchy: find subordinates (bawahan) of a user
    List<UserEntity> findByParentId(Long parentId);

    // Hierarchy: find all users that can be selected as superior (excluding self and own subordinates)
    @Query("SELECT u FROM UserEntity u WHERE u.id <> :userId AND u.status = 1 ORDER BY u.name")
    List<UserEntity> findPotentialSuperiors(@Param("userId") Long userId);

    @Query("SELECT u FROM UserEntity u WHERE "
         + "LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR "
         + "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR "
         + "LOWER(u.npk) LIKE LOWER(CONCAT('%', :search, '%')) OR "
         + "LOWER(u.npp) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<UserEntity> searchUsers(@Param("search") String search, Pageable pageable);

    /**
     * Find a single active user by job title (case-insensitive). Returns the
     * lowest-id match when multiple users share the same job title.
     */
    @Query("SELECT u FROM UserEntity u, JobTitleEntity j "
         + "WHERE u.jobId = j.id "
         + "AND LOWER(j.name) = LOWER(:jobTitle) "
         + "AND (u.status IS NULL OR u.status = 1) "
         + "ORDER BY u.id ASC")
    List<UserEntity> findByJobTitleName(@Param("jobTitle") String jobTitle, Pageable pageable);

    /**
     * Active users whose job title contains the given word (case-insensitive)
     * within any of the given divisions. Used to route JSO findings to the
     * Superintendent of the division chosen on the finding.
     */
    @Query("SELECT u FROM UserEntity u, JobTitleEntity j "
         + "WHERE u.jobId = j.id "
         + "AND LOWER(j.name) LIKE LOWER(CONCAT('%', :jobTitleWord, '%')) "
         + "AND u.divisionId IN :divisionIds "
         + "AND (u.status IS NULL OR u.status = 1)")
    List<UserEntity> findActiveByJobTitleWordAndDivisionIdIn(@Param("jobTitleWord") String jobTitleWord,
            @Param("divisionIds") List<Long> divisionIds);

    // Backward compat alias - delegates to findFirst to avoid NonUniqueResultException
    default Optional<UserEntity> findByEmail(String email) {
        return findFirstByEmailOrderByIdAsc(email);
    }
    default Optional<UserEntity> findByNpp(String npp) {
        return findFirstByNppOrderByIdAsc(npp);
    }
    default Optional<UserEntity> findByNpk(String npk) {
        return findFirstByNpkOrderByIdAsc(npk);
    }
}

