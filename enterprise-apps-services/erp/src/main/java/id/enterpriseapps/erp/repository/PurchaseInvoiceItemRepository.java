package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.PurchaseInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository PurchaseInvoiceItem (tabel purchase_invoice_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface PurchaseInvoiceItemRepository extends JpaRepository<PurchaseInvoiceItem, String>, JpaSpecificationExecutor<PurchaseInvoiceItem> {
}
