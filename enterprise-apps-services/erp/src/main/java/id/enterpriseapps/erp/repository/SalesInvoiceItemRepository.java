package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.SalesInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository SalesInvoiceItem (tabel sales_invoice_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface SalesInvoiceItemRepository extends JpaRepository<SalesInvoiceItem, String>, JpaSpecificationExecutor<SalesInvoiceItem> {
}
