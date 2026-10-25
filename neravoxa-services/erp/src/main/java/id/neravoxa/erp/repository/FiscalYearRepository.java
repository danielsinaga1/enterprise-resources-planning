package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.FiscalYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository FiscalYear (tabel fiscal_years). Dibangkitkan; query khusus taruh di paket domain. */
public interface FiscalYearRepository extends JpaRepository<FiscalYear, String>, JpaSpecificationExecutor<FiscalYear> {
}
