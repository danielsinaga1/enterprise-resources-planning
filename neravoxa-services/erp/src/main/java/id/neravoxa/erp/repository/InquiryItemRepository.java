package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.InquiryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository InquiryItem (tabel inquiry_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface InquiryItemRepository extends JpaRepository<InquiryItem, String>, JpaSpecificationExecutor<InquiryItem> {
}
