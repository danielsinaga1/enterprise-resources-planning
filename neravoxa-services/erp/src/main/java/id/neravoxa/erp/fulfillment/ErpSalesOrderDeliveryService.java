package id.neravoxa.erp.fulfillment;

import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.DeliveryOrder;
import id.neravoxa.erp.entity.DeliveryOrderItem;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.SalesOrder;
import id.neravoxa.erp.entity.SalesOrderItem;
import id.neravoxa.erp.entity.SalesOrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Qty terkirim SO diturunkan dari baris Surat Jalan — port {@code services/salesOrderDelivery.ts}.
 * Satu sumber kebenaran yang dipakai generate-do, edit DO, dan batal DO (tanpa increment/decrement
 * per baris yang mudah bocor).
 */
@Service
public class ErpSalesOrderDeliveryService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /**
     * Tautkan ulang baris DO ke item SO (dicocokkan per inventoryItemId). Baris DO dari SO butuh
     * salesOrderItemId agar qty terkirim tidak kurang hitung; edit DO membuat ulang baris dan
     * kehilangan tautan. Baris yang sudah tertaut atau tanpa item persediaan dibiarkan.
     */
    @ErpTx
    public void relinkDoItemsToSO(String doId) {
        DeliveryOrder d = em.find(DeliveryOrder.class, doId);
        if (d == null || d.getSalesOrderId() == null) {
            return;
        }
        List<Object[]> soItems = em.createQuery(
                        "select s.id, s.inventoryItemId from SalesOrderItem s where s.salesOrderId = :so "
                                + "order by s.createdAt, s.id", Object[].class)
                .setParameter("so", d.getSalesOrderId()).getResultList();
        List<DeliveryOrderItem> lines = em.createQuery(
                        "select i from DeliveryOrderItem i where i.deliveryOrderId = :id order by i.createdAt, i.id",
                        DeliveryOrderItem.class)
                .setParameter("id", doId).getResultList();
        for (DeliveryOrderItem line : lines) {
            if (line.getSalesOrderItemId() != null || line.getInventoryItemId() == null) {
                continue;
            }
            for (Object[] s : soItems) {
                if (Objects.equals(s[1], line.getInventoryItemId())) {
                    line.setSalesOrderItemId((String) s[0]);
                    break;
                }
            }
        }
        em.flush();
    }

    /**
     * Hitung ulang deliveredQty tiap item SO = jumlah baris DO non-batal (lebih kirim diperbolehkan),
     * lalu turunkan ulang status SO. SO Cancelled/Draft hanya disegarkan deliveredQty-nya.
     */
    @ErpTx
    public void recomputeDeliveredQty(String soId) {
        em.flush();
        SalesOrder so = em.find(SalesOrder.class, soId);
        if (so == null) {
            return;
        }
        List<SalesOrderItem> items = em.createQuery(
                        "select s from SalesOrderItem s where s.salesOrderId = :so order by s.createdAt, s.id",
                        SalesOrderItem.class)
                .setParameter("so", soId).getResultList();
        for (SalesOrderItem item : items) {
            BigDecimal delivered = em.createQuery(
                            "select coalesce(sum(i.quantity), 0) from DeliveryOrderItem i join i.deliveryOrder d "
                                    + "where i.salesOrderItemId = :id and d.status <> :cancelled", BigDecimal.class)
                    .setParameter("id", item.getId())
                    .setParameter("cancelled", DocumentStatus.Cancelled)
                    .getSingleResult();
            delivered = Money.nz(delivered);
            if (delivered.compareTo(Money.nz(item.getDeliveredQty())) != 0) {
                item.setDeliveredQty(delivered);
            }
        }
        em.flush();

        if (so.getStatus() == SalesOrderStatus.Cancelled || so.getStatus() == SalesOrderStatus.Draft) {
            return;
        }
        boolean allFully = items.stream()
                .allMatch(it -> Money.nz(it.getDeliveredQty()).compareTo(Money.nz(it.getQuantity())) >= 0);
        boolean anyDelivered = items.stream().anyMatch(it -> Money.nz(it.getDeliveredQty()).signum() > 0);
        SalesOrderStatus next = allFully ? SalesOrderStatus.FullyDelivered
                : anyDelivered ? SalesOrderStatus.PartiallyDelivered : SalesOrderStatus.Submitted;
        if (next != so.getStatus()) {
            so.setStatus(next);
            em.flush();
        }
    }
}
