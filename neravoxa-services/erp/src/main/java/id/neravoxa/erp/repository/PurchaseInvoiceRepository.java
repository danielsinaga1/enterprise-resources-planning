package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.PurchaseInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository PurchaseInvoice (tabel purchase_invoices). Dibangkitkan; query khusus taruh di paket domain. */
public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, String>, JpaSpecificationExecutor<PurchaseInvoice> {
}
