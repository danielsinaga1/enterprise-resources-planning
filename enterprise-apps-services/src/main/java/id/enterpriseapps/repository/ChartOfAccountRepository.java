package id.enterpriseapps.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import id.enterpriseapps.entity.ChartOfAccountEntity;

public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccountEntity, Long> {
    List<ChartOfAccountEntity> findAllByOrderByAccountCodeAsc();
    boolean existsByAccountCode(String accountCode);
}

