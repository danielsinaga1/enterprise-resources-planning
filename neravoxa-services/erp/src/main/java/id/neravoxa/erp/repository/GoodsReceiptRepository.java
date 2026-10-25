package id.neravoxa.erp.repository;

import id.neravoxa.erp.entity.GoodsReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GoodsReceipt (tabel goods_receipts). Dibangkitkan; query khusus taruh di paket domain. */
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, String>, JpaSpecificationExecutor<GoodsReceipt> {
}
