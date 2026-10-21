package id.enterpriseapps.erp.paymentrequest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tagihan EAM (antrian pajak kendaraan, service log, MR sparepart) → bayar lewat jurnal kas/bank atau invoice
 * pembelian (hutang) → batal posting membalik jurnal &amp; invoice.
 */
class PaymentRequestEamTest extends PaymentRequestTestSupport {

    private static final String EAM = BASE + "/eam";

    private record Fixture(String assetCode, String taxId, String tax2Id, String logId, String importLogId, String mrId) {
    }

    private Fixture seed(String userId) {
        String tag = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String branch = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_branches (id, code, name, is_active, created_at) VALUES (?, ?, ?, true, now())",
                branch, "BR-" + tag, "Cabang " + tag);
        String category = jdbc.queryForObject("SELECT id FROM ga_asset_categories WHERE code = 'KENDARAAN'", String.class);
        String asset = UUID.randomUUID().toString();
        String assetCode = "KDR-" + tag;
        jdbc.update("INSERT INTO ga_assets (id, code, name, category_id, branch_id, status, condition, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'GOOD', now(), now())", asset, assetCode, "B " + tag, category, branch);

        String tax = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_vehicle_taxes (id, asset_id, type, period_year, amount, due_date, status, created_at, updated_at) "
                + "VALUES (?, ?, 'PAJAK_KENDARAAN', 2026, 2500000, now(), 'IN_PROCESS', now(), now())", tax, asset);
        // Pajak berstatus ACTIVE tetapi pengajuannya sudah disetujui → tetap masuk antrian.
        String tax2 = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_vehicle_taxes (id, asset_id, type, period_year, amount, due_date, status, created_at, updated_at) "
                + "VALUES (?, ?, 'KIR', 2026, 1000000, now() + interval '10 days', 'ACTIVE', now(), now())", tax2, asset);
        jdbc.update("INSERT INTO ga_vehicle_tax_requests (id, asset_id, requested_by_id, type, period_year, due_date, status, "
                + "tax_id, created_at, updated_at) VALUES (?, ?, ?, 'KIR', 2026, now(), 'APPROVED', ?, now(), now())",
                UUID.randomUUID().toString(), asset, userId, tax2);

        String log = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_vehicle_service_logs (id, asset_id, tanggal, jenis_service, deskripsi, bengkel, biaya, source, created_at) "
                + "VALUES (?, ?, now(), 'SERVIS_BERKALA', 'Servis 10.000 km', 'Bengkel Maju', 750000, 'EAM', now())", log, asset);
        String importLog = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_vehicle_service_logs (id, asset_id, tanggal, jenis_service, deskripsi, biaya, source, created_at) "
                + "VALUES (?, ?, now(), 'OLI', 'Ganti oli (impor)', 300000, 'IMPORT', now())", importLog, asset);

        String mr = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ga_material_requests (id, code, status, asset_id, requested_by_id, requested_at, approved_at, "
                + "created_at, updated_at) VALUES (?, ?, 'APPROVED', ?, ?, now(), now(), now(), now())", mr, "MR-" + tag, asset, userId);
        jdbc.update("INSERT INTO ga_material_request_items (id, material_request_id, item_name, uom, quantity, unit_price, "
                + "received_qty, created_at, updated_at) VALUES (?, ?, 'Kampas rem', 'set', 2, 100000, 0, now(), now())",
                UUID.randomUUID().toString(), mr);
        return new Fixture(assetCode, tax, tax2, log, importLog, mr);
    }

    @Test
    void queue_pay_via_journal_and_invoice_then_cancel() throws Exception {
        String finEmail = email("eam.fin");
        String staffEmail = email("eam.staff");
        String finance = provision(finEmail, "ERP_ACCOUNTANT");
        provision(staffEmail, "EMPLOYEE");
        Fixture f = seed(finance);
        String supplier = createParty("Toko Sparepart " + f.assetCode(), "Supplier");

        // Khusus Keuangan.
        asUser(staffEmail, "EMPLOYEE");
        expectError(403, "Akses ditolak: Tidak memiliki izin.", get(EAM));
        expectError(403, "Akses ditolak: Tidak memiliki izin.", post(EAM + "/VEHICLE_TAX/" + f.taxId() + "/cancel", Map.of()));

        asUser(finEmail, "ERP_ACCOUNTANT");
        expectError(400, "Jenis tagihan tidak dikenal.", get(EAM + "?kind=XYZ"));
        expectError(400, "Status posting tidak dikenal.", get(EAM + "?posting=foo"));

        // Antrian: 2 pajak + 1 service EAM + 1 MR (service IMPORT tidak ikut).
        JsonNode queue = ok(get(EAM + "?search=" + f.assetCode().toLowerCase() + "&sort=amount"));
        assertEquals(4, queue.get("total").asInt());
        JsonNode cheapest = queue.get("data").get(0);
        assertEquals("MATERIAL_REQUEST", cheapest.get("kind").asText());
        assertAmount("200000", cheapest.get("amount"));
        assertEquals("Kampas rem", cheapest.get("items").get(0).get("itemName").asText());
        assertEquals("6.7", cheapest.get("defaultAccountNumber").asText());
        JsonNode service = queue.get("data").get(1);
        assertEquals("SERVICE_LOG", service.get("kind").asText());
        assertEquals("Servis 10.000 km (Bengkel Maju)", service.get("description").asText());
        assertEquals("PENDING", service.get("status").asText());
        JsonNode taxBill = queue.get("data").get(3);
        assertEquals("VEHICLE_TAX", taxBill.get("kind").asText());
        assertEquals("PAJAK KENDARAAN 2026", taxBill.get("ref").asText());
        assertEquals("6.41", taxBill.get("defaultAccountNumber").asText());
        assertTrue(taxBill.get("journalEntry").isNull());

        JsonNode summary = ok(get(EAM + "/summary?search=" + f.assetCode()));
        assertEquals(4, summary.get("matchedCount").asInt());
        assertAmount("4450000", summary.get("matchedAmount"));
        assertTrue(summary.get("pendingCount").asInt() >= 4);

        // Validasi body & jenis.
        expectError(400, "Invalid option: expected one of \"JOURNAL\"|\"PURCHASE_INVOICE\"",
                post(EAM + "/VEHICLE_TAX/" + f.taxId() + "/pay", body("method", "CASH")));
        expectError(400, "Jenis tagihan \"FOO\" tidak dikenal.", post(EAM + "/foo/" + f.taxId() + "/pay", body("method", "JOURNAL")));
        expectError(400, "Supplier wajib dipilih untuk jalur Invoice Pembelian.",
                post(EAM + "/VEHICLE_TAX/" + f.tax2Id() + "/pay", body("method", "PURCHASE_INVOICE")));
        expectError(400, "Service log bersumber \"IMPORT\" (data historis) tidak dapat diposting ke jurnal karena nilainya "
                        + "sudah tercatat di GL lewat input manual.",
                post(EAM + "/SERVICE_LOG/" + f.importLogId() + "/pay", body("method", "JOURNAL")));

        // Pajak via jurnal: DR 6.41 / CR bank pilihan.
        BigDecimal taxExpBefore = balance("6.41");
        BigDecimal bankBefore = balance("1.1.2");
        JsonNode paidTax = ok(post(EAM + "/vehicle_tax/" + f.taxId() + "/pay",
                body("method", "JOURNAL", "creditAccountId", accountId("1.1.2"))));
        assertTrue(paidTax.get("success").asBoolean());
        assertEquals("PAID", paidTax.get("data").get("status").asText());
        assertAmount("2500000", paidTax.get("data").get("paidAmount"));
        String taxJournal = paidTax.get("data").get("journalEntryId").asText();
        List<Map<String, Object>> taxRows = assertBalancedJournal(taxJournal, "Submitted");
        assertAmount("2500000", net(taxRows, "6.41"));
        assertAmount("-2500000", net(taxRows, "1.1.2"));
        assertAmount("2500000", balance("6.41").subtract(taxExpBefore));
        assertAmount("-2500000", balance("1.1.2").subtract(bankBefore));
        expectError(400, "Pajak ini sudah dibayar/terjurnal. Batalkan dulu sebelum posting ulang.",
                post(EAM + "/VEHICLE_TAX/" + f.taxId() + "/pay", body("method", "JOURNAL")));

        // Service via jurnal (kernel): default DR 6.7 / CR kas pertama; respons = jurnal + items.
        JsonNode paidLog = ok(post(EAM + "/SERVICE_LOG/" + f.logId() + "/pay", body("method", "JOURNAL")));
        JsonNode logJournal = paidLog.get("data");
        assertEquals(2, logJournal.get("items").size());
        List<Map<String, Object>> logRows = assertBalancedJournal(logJournal.get("id").asText(), "Submitted");
        assertAmount("750000", net(logRows, "6.7"));
        assertAmount("-750000", net(logRows, "1.1.1"));

        // MR via invoice pembelian: hutang ke supplier.
        JsonNode paidMr = ok(post(EAM + "/MATERIAL_REQUEST/" + f.mrId() + "/pay",
                body("method", "PURCHASE_INVOICE", "partyId", supplier, "dueDate", today(), "notes", "Termin 30 hari")));
        assertEquals("PAID", paidMr.get("data").get("status").asText());
        String invoiceId = paidMr.get("data").get("purchaseInvoiceId").asText();
        Map<String, Object> inv = jdbc.queryForMap("SELECT invoice_number, status, grand_total, outstanding, notes "
                + "FROM purchase_invoices WHERE id = ?", invoiceId);
        assertEquals("Submitted", inv.get("status"));
        assertAmount("200000", (BigDecimal) inv.get("outstanding"));
        assertEquals("Termin 30 hari", inv.get("notes"));
        String mrJournal = paidMr.get("data").get("journalEntryId").asText();
        assertEquals("JV-" + inv.get("invoice_number"),
                jdbc.queryForObject("SELECT entry_number FROM journal_entries WHERE id = ?", String.class, mrJournal));
        List<Map<String, Object>> mrRows = assertBalancedJournal(mrJournal, "Submitted");
        assertAmount("200000", net(mrRows, "6.7"));
        assertAmount("-200000", net(mrRows, "2.1.1"));
        assertAmount("200000", jdbc.queryForObject("SELECT outstanding_amount FROM parties WHERE id = ?", BigDecimal.class, supplier));

        // Filter posting.
        assertEquals(3, ok(get(EAM + "?search=" + f.assetCode() + "&posting=posted")).get("total").asInt());
        assertEquals(1, ok(get(EAM + "?search=" + f.assetCode())).get("total").asInt());
        JsonNode postedMr = ok(get(EAM + "?search=" + f.assetCode() + "&posting=all&kind=material_request")).get("data").get(0);
        assertEquals(inv.get("invoice_number"), postedMr.get("purchaseInvoice").get("invoiceNumber").asText());

        // Batal: MR → invoice batal, hutang hilang, status kembali APPROVED.
        JsonNode cancelMr = ok(post(EAM + "/MATERIAL_REQUEST/" + f.mrId() + "/cancel", Map.of()));
        assertEquals("APPROVED", cancelMr.get("data").get("status").asText());
        assertTrue(cancelMr.get("data").get("journalEntryId").isNull());
        assertEquals("Cancelled", jdbc.queryForObject("SELECT status FROM purchase_invoices WHERE id = ?", String.class, invoiceId));
        assertBalancedJournal(mrJournal, "Cancelled");
        assertAmount("0", jdbc.queryForObject("SELECT outstanding_amount FROM parties WHERE id = ?", BigDecimal.class, supplier));

        JsonNode cancelTax = ok(post(EAM + "/VEHICLE_TAX/" + f.taxId() + "/cancel", Map.of()));
        assertEquals("IN_PROCESS", cancelTax.get("data").get("status").asText());
        assertTrue(cancelTax.get("data").get("paidAmount").isNull());
        assertBalancedJournal(taxJournal, "Cancelled");
        assertAmount("0", balance("6.41").subtract(taxExpBefore));
        assertAmount("0", balance("1.1.2").subtract(bankBefore));

        JsonNode cancelLog = ok(post(EAM + "/SERVICE_LOG/" + f.logId() + "/cancel", Map.of()));
        assertTrue(cancelLog.get("data").get("journalEntryId").isNull());
        assertBalancedJournal(logJournal.get("id").asText(), "Cancelled");
        expectError(400, "Service log ini belum memiliki jurnal.", post(EAM + "/SERVICE_LOG/" + f.logId() + "/cancel", Map.of()));

        // Semua kembali ke antrian.
        assertEquals(4, ok(get(EAM + "?search=" + f.assetCode())).get("total").asInt());
    }
}
