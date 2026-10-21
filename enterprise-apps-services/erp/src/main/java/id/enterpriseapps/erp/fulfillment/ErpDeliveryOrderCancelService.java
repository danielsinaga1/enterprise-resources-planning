package id.enterpriseapps.erp.fulfillment;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.inventory.ErpWarehouseStockService;
import id.enterpriseapps.erp.entity.DeliveryOrder;
import id.enterpriseapps.erp.entity.DeliveryOrderItem;
import id.enterpriseapps.erp.entity.DocumentStatus;
import id.enterpriseapps.erp.entity.StockMovement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Batal Surat Jalan — port {@code services/deliveryOrderCancel.ts}. Ditolak bila sudah diinvoice
 * (batalkan invoice dulu); stok item & gudang dikembalikan, gerakan Out ditandai batal. Tanpa dampak GL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpDeliveryOrderCancelService {

    private final ErpWarehouseStockService stock;
    private final ErpTemporaryDeliveryNoteService temporaryNotes;
    private final ErpSalesOrderDeliveryService soDelivery;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    @ErpTx(timeout = 15)
    public Map<String, Object> cancelInTx(String doId) {
        DeliveryOrder d = em.find(DeliveryOrder.class, doId);
        if (d == null) {
            throw ErpException.business("Surat jalan tidak ditemukan.");
        }
        if (d.getStatus() == DocumentStatus.Cancelled) {
            throw ErpException.business("Surat jalan sudah dibatalkan.");
        }
        if (d.getInvoiceId() != null) {
            throw ErpException.business("Batalkan invoice terkait dulu sebelum batalkan surat jalan.");
        }

        List<DeliveryOrderItem> lines = em.createQuery(
                        "select i from DeliveryOrderItem i left join fetch i.stockMovement where i.deliveryOrderId = :id "
                                + "order by i.createdAt, i.id", DeliveryOrderItem.class)
                .setParameter("id", doId).getResultList();
        for (DeliveryOrderItem line : lines) {
            if (line.getStockMovementId() == null) {
                continue;
            }
            StockMovement sm = line.getStockMovement();
            if (sm == null || Boolean.TRUE.equals(sm.getIsCancelled())) {
                log.warn("DO cancel: linked stockMovement missing or already cancelled, skipping reversal doId={} doNumber={} lineId={} stockMovementId={}",
                        doId, d.getDoNumber(), line.getId(), line.getStockMovementId());
                continue;
            }
            if (line.getInventoryItemId() != null) {
                stock.adjustItemStock(line.getInventoryItemId(), line.getQuantity());
                stock.adjustWarehouseStock(line.getInventoryItemId(), line.getWarehouseId(), line.getQuantity());
            }
            sm.setIsCancelled(true);
        }

        // Status dibalik DULU agar baris DO yang batal keluar dari hitung ulang SO.
        d.setStatus(DocumentStatus.Cancelled);
        d.setCancelledAt(Instant.now());
        em.flush();

        // Surat Jalan Sementara yang tertaut ke DO ini kembali Terbuka.
        temporaryNotes.unlinkFromDeliveryOrder(doId);

        // Turunkan ulang qty terkirim + status SO dari baris DO aktif yang tersisa.
        if (d.getSalesOrderId() != null) {
            soDelivery.recomputeDeliveredQty(d.getSalesOrderId());
        }

        log.info("Delivery order cancelled doId={} doNumber={}", doId, d.getDoNumber());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", d.getId());
        out.put("status", "Cancelled");
        return out;
    }
}
