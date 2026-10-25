package id.neravoxa.erp.purchase;

import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.entity.GoodsReceiptItem;
import id.neravoxa.erp.entity.PurchaseOrderItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Memuat referensi STT/PO untuk baris invoice pembelian lalu menjalankan three-way match murni
 * — port {@code threeWayMatchLoad.ts}. Hanya baca; dipakai detail PI dan {@code POST /match-preview}.
 */
@Service
public class ErpThreeWayMatchService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Bentuk baris minimal — cocok untuk payload create maupun baris tersimpan. */
    public record MatchLine(String id, String itemName, String unit, BigDecimal quantity, BigDecimal timbanganDiterima,
                            BigDecimal rate, BigDecimal discount, String goodsReceiptItemId) {
    }

    @ErpTx(readOnly = true)
    public Map<String, Object> match(List<MatchLine> lines) {
        Set<String> grIds = new LinkedHashSet<>();
        for (MatchLine l : lines) {
            if (l.goodsReceiptItemId() != null && !l.goodsReceiptItemId().isEmpty()) {
                grIds.add(l.goodsReceiptItemId());
            }
        }
        Map<String, GoodsReceiptItem> byId = new HashMap<>();
        if (!grIds.isEmpty()) {
            em.createQuery("select gi from GoodsReceiptItem gi join fetch gi.goodsReceipt "
                            + "left join fetch gi.purchaseOrderItem poi left join fetch poi.purchaseOrder "
                            + "where gi.id in :ids", GoodsReceiptItem.class)
                    .setParameter("ids", grIds)
                    .getResultList()
                    .forEach(g -> byId.put(g.getId(), g));
        }

        List<ThreeWayMatch.LineInput> inputs = new ArrayList<>();
        for (int idx = 0; idx < lines.size(); idx++) {
            MatchLine l = lines.get(idx);
            GoodsReceiptItem gr = l.goodsReceiptItemId() != null ? byId.get(l.goodsReceiptItemId()) : null;
            PurchaseOrderItem po = gr != null ? gr.getPurchaseOrderItem() : null;
            double qty = JsNumbers.n(PurchaseCalc.effectiveQuantity(l.quantity(), l.timbanganDiterima()));
            double disc = JsNumbers.n(l.discount());
            String key = l.id() != null ? l.id() : l.goodsReceiptItemId() != null ? l.goodsReceiptItemId() : String.valueOf(idx);
            inputs.add(new ThreeWayMatch.LineInput(
                    key,
                    l.itemName() == null ? "" : l.itemName(),
                    l.unit(),
                    qty,
                    JsNumbers.n(l.rate()) * (1 - disc / 100),
                    gr != null ? JsNumbers.n(gr.getQuantity()) : null,
                    gr != null ? gr.getGoodsReceipt().getSttNumber() : null,
                    po != null ? po.getId() : null,
                    po != null ? po.getPurchaseOrder().getPoNumber() : null,
                    po != null ? JsNumbers.n(po.getQuantity()) : null,
                    po != null ? JsNumbers.n(po.getEstimatedRate()) * (1 - JsNumbers.n(po.getDiscount()) / 100) : null));
        }
        return ThreeWayMatch.compute(inputs, ThreeWayMatch.DEFAULT_TOLERANCE);
    }
}
