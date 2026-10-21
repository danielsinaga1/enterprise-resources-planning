package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.CompanySettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository CompanySettings (tabel company_settings). Dibangkitkan; query khusus taruh di paket domain. */
public interface CompanySettingsRepository extends JpaRepository<CompanySettings, String>, JpaSpecificationExecutor<CompanySettings> {
}
