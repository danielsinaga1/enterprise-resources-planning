package id.enterpriseapps.erp.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.security.ErpAccess;
import id.enterpriseapps.erp.purchase.PurchaseRequests.CreateGoodsReceiptRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * STT / BPB (penerimaan barang) — port {@code routes/goodsReceipts.ts} (mount {@code /api/goods-receipts}).
 * Gerbang modul {@code goods_receipt} dijalankan ErpRouteGuard.
 */
@RestController
@RequestMapping("/api/erp/goods-receipts")
@RequiredArgsConstructor
public class ErpGoodsReceiptController {

    private final ErpAccess access;
    private final ErpPurchaseBodyParser parser;
    private final ErpGoodsReceiptService receipts;
    private final ErpGoodsReceiptQueryService queries;

    /** Query: page, limit (≤200), status, partyId, startDate, endDate, search, includeLegacy, sort. */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return queries.list(query);
    }

    /** STT belum di-invoice untuk modal PI. */
    @GetMapping("/pending")
    public Map<String, Object> pending(@RequestParam Map<String, String> query) {
        return queries.pending(query.get("partyId"));
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable String id) {
        return queries.detail(id);
    }

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody(required = false) JsonNode body) {
        CreateGoodsReceiptRequest req = parser.parse(body, CreateGoodsReceiptRequest.class);
        return ResponseEntity.status(201).body(receipts.submit(req, access.userId()));
    }

    /** Body opsional: { allowNegativeStock: true, negativeStockReason } untuk pembatalan paksa. */
    @PostMapping("/{id}/cancel")
    public Object cancel(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        access.requireCap("doc.cancel");
        JsonNode allow = body == null ? null : body.get("allowNegativeStock");
        boolean allowNegativeStock = allow != null && allow.isBoolean() && allow.asBoolean();
        JsonNode reasonNode = body == null ? null : body.get("negativeStockReason");
        String reason = reasonNode != null && reasonNode.isTextual() ? reasonNode.asText() : null;
        if (allowNegativeStock && (reason == null || reason.trim().length() < 10)) {
            throw ErpException.business("Alasan bypass stok minus wajib diisi (minimal 10 karakter).");
        }
        return receipts.cancel(id, access.userId(), allowNegativeStock, reason);
    }
}
