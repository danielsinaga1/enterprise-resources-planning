package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.TaxConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository TaxConfig (tabel tax_configs). Dibangkitkan; query khusus taruh di paket domain. */
public interface TaxConfigRepository extends JpaRepository<TaxConfig, String>, JpaSpecificationExecutor<TaxConfig> {
}
