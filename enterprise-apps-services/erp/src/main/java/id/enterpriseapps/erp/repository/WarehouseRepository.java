package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository Warehouse (tabel warehouses). Dibangkitkan; query khusus taruh di paket domain. */
public interface WarehouseRepository extends JpaRepository<Warehouse, String>, JpaSpecificationExecutor<Warehouse> {
}
