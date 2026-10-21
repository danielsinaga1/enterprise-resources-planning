package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.SalesInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository SalesInvoice (tabel sales_invoices). Dibangkitkan; query khusus taruh di paket domain. */
public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, String>, JpaSpecificationExecutor<SalesInvoice> {
}
