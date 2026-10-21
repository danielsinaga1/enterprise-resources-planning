package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository StockTransfer (tabel stock_transfers). Dibangkitkan; query khusus taruh di paket domain. */
public interface StockTransferRepository extends JpaRepository<StockTransfer, String>, JpaSpecificationExecutor<StockTransfer> {
}
