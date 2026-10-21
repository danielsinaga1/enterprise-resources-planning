package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GaDocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaDocumentVersion (tabel ga_document_versions). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaDocumentVersionRepository extends JpaRepository<GaDocumentVersion, String>, JpaSpecificationExecutor<GaDocumentVersion> {
}
