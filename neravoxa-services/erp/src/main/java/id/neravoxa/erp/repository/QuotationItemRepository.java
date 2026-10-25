package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.QuotationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository QuotationItem (tabel quotation_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface QuotationItemRepository extends JpaRepository<QuotationItem, String>, JpaSpecificationExecutor<QuotationItem> {
}
