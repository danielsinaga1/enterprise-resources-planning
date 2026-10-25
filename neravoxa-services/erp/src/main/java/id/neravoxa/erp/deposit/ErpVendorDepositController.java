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
 * Uang muka supplier — port {@code routes/vendorDeposits.ts} (mount {@code /api/vendor-deposits}).
 * Gerbang modul {@code vendor_deposit} dijalankan ErpRouteGuard (mencakup /summary juga); di sini hanya
 * capability {@code doc.cancel} untuk batal aplikasi.
 */
@RestController
@RequestMapping("/api/erp/vendor-deposits")
@RequiredArgsConstructor
@Tag(name = "ERP - Uang Muka Supplier", description = "Daftar, saldo, aplikasi ke invoice pembelian, refund/offset, batal")
public class ErpVendorDepositController {

    private final ErpDepositQueryService queries;
    private final ErpVendorDepositService deposits;
    private final ErpDepositRequestValidator validator;
    private final ErpAccess access;

    /** Dua mode seperti uang muka pelanggan (lihat {@link ErpCustomerDepositController#list}). */
    @GetMapping
    @Operation(summary = "Daftar uang muka supplier (mode lama atau berhalaman)")
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return queries.list(DepositKind.VENDOR, query);
    }

    @GetMapping("/summary")
    @Operation(summary = "Ringkasan KPI uang muka supplier (filter sama dengan daftar)")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return queries.summary(DepositKind.VENDOR, query);
    }

    @GetMapping("/balance/{partyId}")
    @Operation(summary = "Saldo uang muka per supplier")
    public Map<String, Object> balance(@PathVariable String partyId) {
        return queries.balance(DepositKind.VENDOR, partyId);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Batalkan uang muka supplier (membalik jurnal pembayaran)")
    public Map<String, Object> cancel(@PathVariable String id) {
        return deposits.cancel(id);
    }

    @PostMapping("/apply")
    @Operation(summary = "Aplikasikan uang muka ke invoice pembelian (DR Hutang / CR 1.3)")
    public ResponseEntity<Map<String, Object>> apply(@RequestBody(required = false) DepositRequests.ApplyVendor body) {
        validator.check(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(deposits.apply(body, access.userId()));
    }

    @PostMapping("/apply/{id}/cancel")
    @Operation(summary = "Batalkan aplikasi uang muka supplier (butuh doc.cancel)")
    public Map<String, Object> cancelApplication(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return deposits.cancelApplication(id);
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Selesaikan sisa uang muka: offset hutang (FIFO) dan/atau refund kas dari vendor")
    public Map<String, Object> refund(@PathVariable String id,
                                      @RequestBody(required = false) DepositRequests.Refund body) {
        validator.checkRefund(body, DepositKind.VENDOR);
        return deposits.refund(id, body, access.userId());
    }
}
