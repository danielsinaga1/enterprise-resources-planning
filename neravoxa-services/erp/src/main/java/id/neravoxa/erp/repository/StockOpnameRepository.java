package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.StockOpname;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository StockOpname (tabel stock_opnames). Dibangkitkan; query khusus taruh di paket domain. */
public interface StockOpnameRepository extends JpaRepository<StockOpname, String>, JpaSpecificationExecutor<StockOpname> {
}
