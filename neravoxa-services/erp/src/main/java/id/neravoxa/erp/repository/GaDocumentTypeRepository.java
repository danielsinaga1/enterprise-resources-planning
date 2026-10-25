package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GaDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GaDocumentType (tabel ga_document_types). Dibangkitkan; query khusus taruh di paket domain. */
public interface GaDocumentTypeRepository extends JpaRepository<GaDocumentType, String>, JpaSpecificationExecutor<GaDocumentType> {
}
