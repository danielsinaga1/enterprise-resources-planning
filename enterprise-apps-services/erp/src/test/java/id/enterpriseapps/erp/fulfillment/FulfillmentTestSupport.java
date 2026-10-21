package id.enterpriseapps.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.approval.ErpApprovalGateService;
import id.enterpriseapps.erp.core.approval.ErpApprovalHandler;
import id.enterpriseapps.erp.core.approval.ErpApprovalHandlerRegistry;
import id.enterpriseapps.erp.entity.ApprovalRequest;
import id.enterpriseapps.erp.repository.ApprovalRequestRepository;
import id.enterpriseapps.erp.support.ErpIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Fixture bersama uji paket pengiriman penjualan (data unik per test; DB tidak di-rollback antar test). */
abstract class FulfillmentTestSupport extends ErpIntegrationTest {

    static final String SO = "/api/erp/sales-orders";
    static final String DO = "/api/erp/delivery-orders";
    static final String TDN = "/api/erp/temporary-delivery-notes";
    static final String RETURNS = "/api/erp/sales-returns";
    static final String INVOICES = "/api/erp/sales/invoices";

    @Autowired
    ErpApprovalHandlerRegistry handlers;
    @Autowired
    ApprovalRequestRepository requests;
    @Autowired
    @Qualifier(ErpTx.MANAGER)
    PlatformTransactionManager tm;

    static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    /** Map berurutan yang menerima nilai null. */
    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /** Admin ERP terprovisi; kembalikan id-nya. */
    String adminUserId() throws Exception {
        asAdmin();
        ok(get("/api/erp/me"));
        return userId("erp.admin@test.local");
    }

    String userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", String.class, email);
    }

    String customer(String name) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO parties (id, name, party_type, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'Customer', true, now(), now())", id, name);
        return id;
    }

    String defaultWarehouseId() {
        return jdbc.queryForObject("SELECT id FROM warehouses WHERE code = 'GDG-UTAMA'", String.class);
    }

    /** Item persediaan (satuan kg) dengan averageCost + stok awal di gudang default. */
    String item(String name, String averageCost, String stockQty) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO inventory_items (id, code, name, unit, current_stock, average_cost, is_active, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'kg', CAST(? AS numeric), CAST(? AS numeric), true, now(), now())",
                id, uniq("ITM"), name, stockQty, averageCost);
        if (new BigDecimal(stockQty).signum() != 0) {
            jdbc.update("INSERT INTO inventory_stocks (id, item_id, warehouse_id, current_stock, created_at, updated_at) "
                    + "VALUES (?, ?, ?, CAST(? AS numeric), now(), now())", UUID.randomUUID().toString(), id, defaultWarehouseId(), stockQty);
        }
        return id;
    }

    BigDecimal itemStock(String itemId) {
        return jdbc.queryForObject("SELECT current_stock FROM inventory_items WHERE id = ?", BigDecimal.class, itemId);
    }

    BigDecimal whStock(String itemId) {
        return jdbc.queryForObject("SELECT COALESCE((SELECT current_stock FROM inventory_stocks WHERE item_id = ? AND warehouse_id = ?), 0)",
                BigDecimal.class, itemId, defaultWarehouseId());
    }

    String accountId(String number) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = ?", String.class, number);
    }

    BigDecimal balance(String number) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE account_number = ?", BigDecimal.class, number);
    }

    BigDecimal partyColumn(String partyId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM parties WHERE id = ?", BigDecimal.class, partyId);
    }

    /** Jurnal seimbang & ledger sejumlah baris jurnal; kembalikan total debit. */
    BigDecimal assertBalancedJournal(String entryNumber, String expectedStatus) {
        Map<String, Object> je = jdbc.queryForMap("SELECT id, status FROM journal_entries WHERE entry_number = ?", entryNumber);
        assertEquals(expectedStatus, je.get("status"));
        String jeId = (String) je.get("id");
        BigDecimal dr = jdbc.queryForObject("SELECT COALESCE(SUM(debit),0) FROM journal_entry_accounts WHERE journal_entry_id = ?",
                BigDecimal.class, jeId);
        BigDecimal cr = jdbc.queryForObject("SELECT COALESCE(SUM(credit),0) FROM journal_entry_accounts WHERE journal_entry_id = ?",
                BigDecimal.class, jeId);
        assertEquals(0, dr.compareTo(cr), "jurnal " + entryNumber + " tidak seimbang");
        Integer items = jdbc.queryForObject("SELECT COUNT(*) FROM journal_entry_accounts WHERE journal_entry_id = ?", Integer.class, jeId);
        Integer ledger = jdbc.queryForObject("SELECT COUNT(*) FROM accounting_ledger_entries WHERE reference_id = ?", Integer.class, jeId);
        assertEquals(items, ledger);
        return dr;
    }

    /** Jalankan handler approval seperti halaman Approval (di dalam transaksi keputusan). */
    ErpApprovalHandler.ApprovedDocument approve(String requestId, String approverId) {
        return new TransactionTemplate(tm).execute(s -> {
            ApprovalRequest r = requests.findById(requestId).orElseThrow();
            ErpApprovalHandler h = handlers.find(r.getDocumentType()).orElseThrow();
            JsonNode[] split = ErpApprovalGateService.splitStoredPayload(r.getPayload());
            return h.approve(r, split[0], split[1], approverId, "");
        });
    }

    static void assertAmount(String expected, Object actual) {
        BigDecimal a = actual instanceof JsonNode n ? n.decimalValue() : new BigDecimal(String.valueOf(actual));
        assertEquals(0, new BigDecimal(expected).compareTo(a), "nilai " + actual + " ≠ " + expected);
    }
}
