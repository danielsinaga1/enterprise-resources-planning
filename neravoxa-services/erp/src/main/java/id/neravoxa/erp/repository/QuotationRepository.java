package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository Quotation (tabel quotations). Dibangkitkan; query khusus taruh di paket domain. */
public interface QuotationRepository extends JpaRepository<Quotation, String>, JpaSpecificationExecutor<Quotation> {
}
