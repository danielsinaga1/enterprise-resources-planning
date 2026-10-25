package id.neravoxa.erp.cashbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.security.ErpAccess;
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
 * Pembayaran / penerimaan kas &amp; bank — port {@code routes/payments.ts} (mount {@code /api/payments},
 * gerbang modul {@code payment} dijalankan ErpRouteGuard).
 */
@RestController
@RequestMapping("/api/erp/payments")
@RequiredArgsConstructor
public class ErpPaymentController {

    private final ErpAccess access;
    private final ErpPaymentService payments;
    private final ErpPaymentSubmitService submitter;
    private final ErpCashLedgerService ledger;

    /** Query: page (default 1), limit (default 50, maks. 200). → { data, total, page, limit }. */
    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return payments.list(query);
    }

    /** Baris jurnal yang menyentuh akun kas/bank dan bukan jurnal pembayaran. → { data }. */
    @GetMapping("/cash-journals")
    public Map<String, Object> cashJournals() {
        return payments.cashJournals();
    }

    /**
     * Daftar "Transaksi Kas &amp; Bank" (pembayaran + baris jurnal kas) berpaginasi server. Query: page,
     * limit (≤500), search, type, direction, status, partyId, accountId, startDate, endDate, sort
     * (date | number | party | amount, "-" = turun). → { data, total, page, limit }.
     */
    @GetMapping("/ledger")
    public Map<String, Object> ledger(@RequestParam Map<String, String> query) {
        return ledger.list(query);
    }

    /** KPI (count, uang masuk/keluar, net) untuk filter yang sama dengan /ledger. */
    @GetMapping("/ledger/summary")
    public Map<String, Object> ledgerSummary(@RequestParam Map<String, String> query) {
        return ledger.summary(query);
    }

    @GetMapping("/{id}")
    public ObjectNode detail(@PathVariable String id) {
        return payments.detail(id);
    }

    /** Catat pembayaran/penerimaan (6 jenis paymentType + alokasi otomatis). 201 { ...payment, party, unallocatedAmount }. */
    @PostMapping
    public ResponseEntity<ObjectNode> create(@RequestBody(required = false) JsonNode body) {
        CashBankRequests.CreatePayment req = CashBankRequests.CreatePayment.parse(body);
        return ResponseEntity.status(201).body(submitter.submit(req, access.userId()).toJson());
    }

    /** Batalkan pembayaran Receive/Pay dan balik alokasinya (hak {@code doc.cancel}). */
    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return payments.cancel(id);
    }
}
