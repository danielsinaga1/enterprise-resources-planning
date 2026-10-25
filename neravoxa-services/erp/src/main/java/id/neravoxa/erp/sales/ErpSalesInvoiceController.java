package id.neravoxa.erp.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.security.ErpAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Invoice penjualan — port {@code routes/sales.ts} (mount {@code /api/sales/invoices}).
 * Gerbang modul {@code sales_invoice} (view/edit) dijalankan ErpRouteGuard.
 */
@RestController
@RequestMapping("/api/erp/sales/invoices")
@RequiredArgsConstructor
@Tag(name = "ERP - Invoice Penjualan", description = "Invoice penjualan: daftar, detail, submit (posting GL), edit metadata, batal")
public class ErpSalesInvoiceController {

    private final ErpSalesInvoiceService invoices;
    private final ErpSalesInvoiceSubmitService submitter;
    private final ErpSalesInvoiceCancelService canceller;
    private final ErpSalesRequestValidator validator;
    private final ErpAccess access;

    /** Query: page, limit (≤500), status, partyId, startDate, endDate (YYYY-MM-DD), search, sort. */
    @GetMapping
    @Operation(summary = "Daftar invoice penjualan → { data, total, page, limit }")
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return invoices.list(query);
    }

    /** KPI dengan filter yang sama dengan daftar (dideklarasikan sebelum /:id). */
    @GetMapping("/summary")
    @Operation(summary = "Ringkasan KPI invoice penjualan")
    public Map<String, Object> summary(@RequestParam Map<String, String> query) {
        return invoices.summary(query);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail invoice penjualan + alokasi pembayaran + uang muka")
    public ObjectNode detail(@PathVariable String id) {
        return invoices.detail(id);
    }

    /** Submit invoice (validasi, jurnal multi-akun, HPP dari DO) — logika di ErpSalesInvoiceSubmitService. */
    @PostMapping
    @Operation(summary = "Buat & submit invoice penjualan (posting GL)")
    public ResponseEntity<ObjectNode> create(@RequestBody SalesInvoiceRequests.Create body) {
        validator.check(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(submitter.submit(body, access.userId()));
    }

    /** Edit metadata invoice (notes, dueDate, terms). */
    @PutMapping("/{id}")
    @Operation(summary = "Edit catatan, jatuh tempo, syarat invoice")
    public JsonNode update(@PathVariable String id, @RequestBody SalesInvoiceRequests.MetaUpdate body) {
        validator.check(body);
        return invoices.updateMeta(id, body);
    }

    /** Batalkan invoice (butuh capability doc.cancel). */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "Batalkan invoice penjualan (membalik jurnal)")
    public Map<String, Object> cancel(@PathVariable String id) {
        access.requireCap("doc.cancel");
        return canceller.cancel(id);
    }
}
