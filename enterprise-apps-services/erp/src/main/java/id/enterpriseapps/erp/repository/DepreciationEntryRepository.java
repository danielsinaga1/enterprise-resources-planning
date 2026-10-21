package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.DepreciationEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository DepreciationEntry (tabel depreciation_entries). Dibangkitkan; query khusus taruh di paket domain. */
public interface DepreciationEntryRepository extends JpaRepository<DepreciationEntry, String>, JpaSpecificationExecutor<DepreciationEntry> {
}
