package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository PaymentAllocation (tabel payment_allocations). Dibangkitkan; query khusus taruh di paket domain. */
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, String>, JpaSpecificationExecutor<PaymentAllocation> {
}
