package id.neravoxa.erp.deposit;

import id.neravoxa.erp.core.security.ErpAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Uang muka pelanggan — port {@code routes/customerDeposits.ts} (mount {@code /api/customer-deposits}).
 * Gerbang modul {@code customer_deposit} dijalankan ErpRouteGuard; di sini hanya cek di dalam handler
 * (capability {@code doc.cancel} untuk batal aplikasi).
 */
@RestController
@RequestMapping("/api/erp/customer-deposits")
@RequiredArgsConstructor
@Tag(name = "ERP - Uang Muka Pelanggan", description = "Daftar, saldo, aplikasi ke invoice penjualan, refund/offset, batal")
public class ErpCustomerDepositController {

    private final ErpDepositQueryService queries;
    private final ErpCustomerDepositService deposits;
    private final ErpDepositRequestValidator validator;
    private final ErpAccess access;

    /**
     * Tanpa {@code page}: semua uang muka (filter partyId, status DocumentStatus) → {@code { data }}.
     * Dengan {@code page}: page, limit (≤200), status (Active | PartiallyUsed | FullyUsed | Draft | Submitted |
     * Cancelled), partyId, startDate/endDate, search, sort → {@code { data, total, page, limit }}.
     */
    @GetMapping
    @Operation(summary = "Daftar uang muka pelanggan (mode lama atau berhalaman)")
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return queries.list(DepositKind.CUSTOMER, query);
    }

    @GetMapping("/summary")
    @Operation(summary = "Ringkasan KPI uang muka pelanggan (filter sama dengan daftar)")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return queries.summary(DepositKind.CUSTOMER, query);
    }

    @GetMapping("/balance/{partyId}")
    @Operation(summary = "Saldo uang muka pelanggan per customer")
    public Map<String, Object> balance(@PathVariable String partyId) {
        return queries.balance(DepositKind.CUSTOMER, partyId);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Batalkan uang muka pelanggan (membalik jurnal penerimaan)")
    public Map<String, Object> cancel(@PathVariable String id) {
        return deposits.cancel(id);
    }

    @PostMapping("/apply")
    @Operation(summary = "Aplikasikan uang muka ke invoice penjualan (DR 2.1.2 / CR Piutang)")
    public ResponseEntity<Map<String, Object>> apply(@RequestBody(required = false) DepositRequests.ApplyCustomer body) {
        validator.check(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(deposits.apply(body, access.userId()));
    }

    @PostMapping("/apply/{id}/cancel")
    @Operation(summary = "Batalkan aplikasi uang muka pelanggan (butuh doc.cancel)")
    public Map<String, Object> cancelApplication(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return deposits.cancelApplication(id);
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Selesaikan sisa uang muka: offset piutang (FIFO) dan/atau refund kas")
    public Map<String, Object> refund(@PathVariable String id,
                                      @RequestBody(required = false) DepositRequests.Refund body) {
        validator.checkRefund(body, DepositKind.CUSTOMER);
        return deposits.refund(id, body, access.userId());
    }
}
