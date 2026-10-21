package id.enterpriseapps.erp.repository;

import id.enterpriseapps.erp.entity.GoodsReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository GoodsReceiptItem (tabel goods_receipt_items). Dibangkitkan; query khusus taruh di paket domain. */
public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItem, String>, JpaSpecificationExecutor<GoodsReceiptItem> {
}
