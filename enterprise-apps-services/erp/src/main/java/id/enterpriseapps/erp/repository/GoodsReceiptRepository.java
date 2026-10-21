package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GoodsReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GoodsReceipt (tabel goods_receipts). Dibangkitkan; query khusus taruh di paket domain. */
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, String>, JpaSpecificationExecutor<GoodsReceipt> {
}
