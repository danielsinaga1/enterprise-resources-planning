package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GaDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaDocument (tabel ga_documents). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaDocumentRepository extends JpaRepository<GaDocument, String>, JpaSpecificationExecutor<GaDocument> {
}
