package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.ServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository ServiceItem (tabel service_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface ServiceItemRepository extends JpaRepository<ServiceItem, String>, JpaSpecificationExecutor<ServiceItem> {
}
