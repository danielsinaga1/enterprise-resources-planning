package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository Payment (tabel payments). Dibangkitkan; query khusus taruh di paket domain. */
public interface PaymentRepository extends JpaRepository<Payment, String>, JpaSpecificationExecutor<Payment> {
}
