package id.neravoxa.erp.toll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.security.ErpAccess;
import id.neravoxa.erp.core.security.ErpPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Jasa Maklon & stok titipan pelanggan — {@code /api/erp/toll/**}. Gerbang modul {@code toll_service}
 * (GET = view, tulis = edit) dijalankan ErpRouteGuard; pembatalan butuh {@code doc.cancel}, membuat invoice
 * dari order juga butuh izin edit {@code sales_invoice}.
 */
@RestController
@RequestMapping("/api/erp/toll")
@RequiredArgsConstructor
@Tag(name = "ERP - Jasa Maklon", description = "Order jasa maklon, stok titipan pelanggan, surat jalan pengembalian, laporan rendemen")
public class ErpTollController {

    private final ErpAccess access;
    private final ErpTollOrderService orders;
    private final ErpCustomerGoodsReturnService returns;
    private final ErpCustomerStockService stock;

    // ── Order Jasa Maklon ──

    @GetMapping("/orders")
    @Operation(summary = "Daftar order jasa maklon → { data, total, page, limit }")
    public Map<String, Object> listOrders(@RequestParam Map<String, String> query) {
        return orders.list(query);
    }

    @GetMapping("/orders/options")
    @Operation(summary = "Order yang bisa dipilih di STT / produksi / pengembalian (use=RECEIVE|PROCESS|RETURN)")
    public List<Map<String, Object>> orderOptions(@RequestParam(required = false) String partyId,
                                                  @RequestParam(required = false) String use) {
        return orders.options(partyId, use);
    }

    @GetMapping("/orders/defaults")
    @Operation(summary = "Tarif default (tarif khusus pelanggan atau item jasa)")
    public Map<String, Object> orderDefaults(@RequestParam(required = false) String partyId,
                                             @RequestParam(required = false) String serviceItemId) {
        return orders.defaults(partyId, serviceItemId);
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> orderDetail(@PathVariable String id) {
        return orders.detail(id);
    }

    @GetMapping("/orders/{id}/billing")
    public Map<String, Object> orderBilling(@PathVariable String id) {
        return orders.billing(id);
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody(required = false) JsonNode body) {
        return ResponseEntity.status(201).body(orders.create(TollRequests.OrderInput.parse(body), access.userId()));
    }

    @PutMapping("/orders/{id}")
    public Map<String, Object> updateOrder(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        return orders.update(id, TollRequests.OrderInput.parse(body));
    }

    @PostMapping("/orders/{id}/complete")
    public Map<String, Object> completeOrder(@PathVariable String id) {
        return orders.complete(id);
    }

    @PostMapping("/orders/{id}/reopen")
    public Map<String, Object> reopenOrder(@PathVariable String id) {
        return orders.reopen(id);
    }

    @PostMapping("/orders/{id}/cancel")
    public Map<String, Object> cancelOrder(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return orders.cancel(id);
    }

    @PostMapping("/orders/{id}/invoice")
    @Operation(summary = "Buat & submit invoice penjualan dari tagihan order yang belum ditagih")
    public ResponseEntity<ObjectNode> invoiceOrder(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        access.requireCan("sales_invoice", ErpPermissionService.EDIT);
        return ResponseEntity.status(201).body(orders.invoice(id, TollRequests.InvoiceInput.parse(body), access.userId()));
    }

    // ── Stok titipan ──

    @GetMapping("/stock/balances")
    public Map<String, Object> balances(@RequestParam Map<String, String> query) {
        return stock.balances(query);
    }

    @GetMapping("/stock/card")
    public Map<String, Object> card(@RequestParam Map<String, String> query) {
        return stock.card(query);
    }

    @PostMapping("/stock/transfer")
    public ResponseEntity<Map<String, Object>> transfer(@RequestBody(required = false) JsonNode body) {
        return ResponseEntity.status(201).body(stock.transfer(TollRequests.TransferInput.parse(body), access.userId()));
    }

    @PostMapping("/stock/transfer/{id}/cancel")
    public Map<String, Object> cancelTransfer(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return stock.cancelTransfer(id);
    }

    // ── Surat Jalan Pengembalian Titipan ──

    @GetMapping("/returns")
    public Map<String, Object> listReturns(@RequestParam Map<String, String> query) {
        return returns.list(query);
    }

    @GetMapping("/returns/{id}")
    public Map<String, Object> returnDetail(@PathVariable String id) {
        return returns.detail(id);
    }

    @PostMapping("/returns")
    public ResponseEntity<Map<String, Object>> createReturn(@RequestBody(required = false) JsonNode body) {
        return ResponseEntity.status(201).body(returns.create(TollRequests.ReturnInput.parse(body), access.userId()));
    }

    @PostMapping("/returns/{id}/cancel")
    public Map<String, Object> cancelReturn(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return returns.cancel(id);
    }

    // ── Laporan ──

    @GetMapping("/reports/yield")
    @Operation(summary = "Rendemen maklon per order & pelanggan")
    public Map<String, Object> yieldReport(@RequestParam Map<String, String> query) {
        return stock.yieldReport(query);
    }

    @GetMapping("/reports/storage")
    @Operation(summary = "Biaya simpan titipan per order dalam periode")
    public Map<String, Object> storageReport(@RequestParam Map<String, String> query) {
        return stock.storageReport(query);
    }

    // ── Tarif jasa per pelanggan ──

    @GetMapping("/rates")
    public List<Map<String, Object>> rates(@RequestParam(required = false) String serviceItemId,
                                           @RequestParam(required = false) String partyId) {
        return stock.rates(serviceItemId, partyId);
    }

    @PostMapping("/rates")
    public ResponseEntity<Map<String, Object>> saveRate(@RequestBody(required = false) JsonNode body) {
        return ResponseEntity.status(201).body(stock.saveRate(TollRequests.RateInput.parse(body)));
    }

    @DeleteMapping("/rates/{id}")
    public Map<String, Object> deleteRate(@PathVariable String id) {
        return stock.deleteRate(id);
    }
}
