package id.neravoxa.erp.paymentrequest;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.security.ErpAccess;
import id.neravoxa.erp.paymentrequest.PrRequests.EamPay;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Tagihan EAM — antrian biaya EAM (pajak kendaraan, service, sparepart MR) di dalam Pengajuan Pembayaran,
 * port {@code routes/paymentRequestEam.ts} (mount {@code /api/payment-requests/eam}). Khusus Keuangan: setiap
 * endpoint butuh izin {@code payment_request} edit.
 *
 * <pre>
 *   GET  /               daftar berpaginasi { data, total, page, limit }
 *   GET  /summary        { pendingCount, pendingAmount, matchedCount, matchedAmount }
 *   POST /:kind/:id/pay  { method: JOURNAL | PURCHASE_INVOICE, accountId, ... }
 *   POST /:kind/:id/cancel
 * </pre>
 */
@RestController
@RequestMapping("/api/erp/payment-requests/eam")
@RequiredArgsConstructor
public class ErpPaymentRequestEamController {

    private final ErpAccess access;
    private final ErpPaymentRequestEamService bills;

    /** {@code requireFinance}: 403 tanpa izin payment_request edit. */
    private void requireFinance() {
        if (!access.can(PrRules.PR_MODULE, "edit")) {
            throw ErpException.forbidden();
        }
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        requireFinance();
        return bills.list(query);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        requireFinance();
        return bills.summary(query);
    }

    @PostMapping("/{kind}/{id}/pay")
    public Map<String, Object> pay(@PathVariable String kind, @PathVariable String id,
                                   @RequestBody(required = false) JsonNode body) {
        requireFinance();
        EamPay input = EamPay.parse(body);
        Object result = bills.pay(kind, id, input, access.userId());
        return PrJson.ordered("success", true, "data", result);
    }

    @PostMapping("/{kind}/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String kind, @PathVariable String id) {
        requireFinance();
        Object result = bills.cancel(kind, id);
        return PrJson.ordered("success", true, "data", result);
    }
}
