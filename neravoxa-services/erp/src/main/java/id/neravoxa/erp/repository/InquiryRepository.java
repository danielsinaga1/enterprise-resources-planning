package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository Inquiry (tabel inquiries). Dibangkitkan; query khusus taruh di paket domain. */
public interface InquiryRepository extends JpaRepository<Inquiry, String>, JpaSpecificationExecutor<Inquiry> {
}
