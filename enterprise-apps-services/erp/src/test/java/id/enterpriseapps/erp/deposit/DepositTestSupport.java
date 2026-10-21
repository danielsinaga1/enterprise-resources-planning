package id.enterpriseapps.erp.deposit;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.accounting.ErpFiscalYearService;
import id.enterpriseapps.erp.core.accounting.ErpGlPostingService;
import id.enterpriseapps.erp.core.accounting.ErpPartyBalanceService;
import id.enterpriseapps.erp.entity.FiscalYear;
import id.enterpriseapps.erp.entity.JournalEntry;
import id.enterpriseapps.erp.support.ErpIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Fixture uji uang muka. Penerimaan/pembayaran uang muka dibuat di rute pembayaran (slice lain), jadi di
 * sini dibuat langsung: jurnal lewat kernel GL (DR Kas / CR 2.1.2 untuk pelanggan, DR 1.3 / CR Kas untuk
 * supplier) + baris {@code payments} + saldo uang muka mitra. Data unik per test (DB tidak di-rollback).
 */
abstract class DepositTestSupport extends ErpIntegrationTest {

    static final String CASH = "1.1.1";
    static final String BANK = "1.1.2";
    static final String AR = "1.2.1";
    static final String AP = "2.1.1";
    static final String CUSTOMER_DEPOSIT = "2.1.2";
    static final String VENDOR_DEPOSIT = "1.3";
    static final String OPENING_EQUITY = "3.1";

    @Autowired
    ErpGlPostingService gl;
    @Autowired
    ErpFiscalYearService fiscalYears;
    @Autowired
    ErpPartyBalanceService partyBalances;
    @Autowired
    @Qualifier(ErpTx.MANAGER)
    PlatformTransactionManager tm;

    static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    /** Pastikan admin ERP terprovisi dan login sebagai admin; kembalikan id pengguna ERP-nya. */
    String adminUserId() throws Exception {
        asAdmin();
        ok(get("/api/erp/me"));
        return jdbc.queryForObject("SELECT id FROM users WHERE email = 'erp.admin@test.local'", String.class);
    }

    String createParty(String name, String type) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO parties (id, name, party_type, is_active, created_at, updated_at) "
                + "VALUES (?, ?, ?, true, now(), now())", id, name, type);
        return id;
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

    String fiscalYearId() {
        return new TransactionTemplate(tm).execute(s -> fiscalYears.getOpenFiscalYear(Instant.now()).getId());
    }

    static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** Uang muka pelanggan Submitted: DR Kas / CR 2.1.2 (party) + customer_deposit_balance += amount. */
    String receiveCustomerDeposit(String partyId, String amount, String userId) {
        return createDeposit(partyId, new BigDecimal(amount), userId, true, true);
    }

    /** Uang muka supplier Submitted: DR 1.3 (party) / CR Kas + deposit_balance += amount. */
    String payVendorDeposit(String partyId, String amount, String userId) {
        return createDeposit(partyId, new BigDecimal(amount), userId, false, true);
    }

    /** Uang muka hasil impor lama (tanpa jurnal tertaut) — saldo mitra saja. */
    String legacyDeposit(String partyId, String amount, String userId, boolean customer) {
        return createDeposit(partyId, new BigDecimal(amount), userId, customer, false);
    }

    private String createDeposit(String partyId, BigDecimal amount, String userId, boolean customer, boolean withJournal) {
        String number = uniq(customer ? "UMC" : "UMV");
        Instant now = Instant.now();
        String cash = accountId(CASH);
        String depositAccount = accountId(customer ? CUSTOMER_DEPOSIT : VENDOR_DEPOSIT);
        String journalId = new TransactionTemplate(tm).execute(s -> {
            FiscalYear fy = fiscalYears.getOpenFiscalYear(now);
            String jeId = null;
            if (withJournal) {
                ErpGlPostingService.Builder b = gl.journal().date(now).fiscalYear(fy.getId()).createdBy(userId);
                if (customer) {
                    b.narration("Uang Muka Pelanggan: " + number)
                            .line(cash, amount, BigDecimal.ZERO, "UM Pelanggan " + number)
                            .line(depositAccount, BigDecimal.ZERO, amount, "UM Pelanggan " + number, partyId);
                } else {
                    b.narration("Uang Muka Supplier: " + number)
                            .line(depositAccount, amount, BigDecimal.ZERO, "UM Supplier " + number, partyId)
                            .line(cash, BigDecimal.ZERO, amount, "UM Supplier " + number);
                }
                JournalEntry je = b.post();
                jeId = je.getId();
            }
            partyBalances.incrementParty(partyId, customer ? "customer_deposit_balance" : "deposit_balance", amount);
            return jeId == null ? "" : jeId;
        });
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO payments (id, payment_number, date, payment_type, party_id, account_id, amount, "
                        + "refunded_amount, status, created_by, journal_entry_id, fiscal_year_id, submitted_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 'Submitted', ?, ?, ?, now(), now(), now())",
                id, number, utc(now), customer ? "CustomerDeposit" : "VendorDeposit", partyId, accountId(CASH), amount,
                userId, journalId.isEmpty() ? null : journalId, fiscalYearId());
        return id;
    }

    /** Invoice penjualan Submitted (outstanding = total) bertanggal {@code daysAgo} hari lalu. */
    String createSalesInvoice(String partyId, String total, int daysAgo, String userId) {
        return createInvoice("sales_invoices", uniq("SI"), partyId, total, daysAgo, userId);
    }

    /** Invoice pembelian Submitted (outstanding = total). */
    String createPurchaseInvoice(String partyId, String total, int daysAgo, String userId) {
        return createInvoice("purchase_invoices", uniq("PI"), partyId, total, daysAgo, userId);
    }

    private String createInvoice(String table, String number, String partyId, String total, int daysAgo, String userId) {
        String id = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal(total);
        jdbc.update("INSERT INTO " + table + " (id, invoice_number, date, party_id, status, grand_total, outstanding, "
                        + "fiscal_year_id, created_by, submitted_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'Submitted', ?, ?, ?, ?, now(), now(), now())",
                id, number, utc(Instant.now().minus(daysAgo, ChronoUnit.DAYS)), partyId, amount, amount, fiscalYearId(), userId);
        return id;
    }

    BigDecimal invoiceOutstanding(String table, String id) {
        return jdbc.queryForObject("SELECT outstanding FROM " + table + " WHERE id = ?", BigDecimal.class, id);
    }

    String invoiceStatus(String table, String id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
    }

    String journalStatus(String journalId) {
        return jdbc.queryForObject("SELECT status FROM journal_entries WHERE id = ?", String.class, journalId);
    }

    /** Jurnal seimbang di ledger dan semua barisnya aktif/batal sesuai harapan. */
    void assertLedger(String journalId, boolean cancelled) {
        BigDecimal net = jdbc.queryForObject(
                "SELECT COALESCE(SUM(debit) - SUM(credit), 0) FROM accounting_ledger_entries WHERE reference_id = ?",
                BigDecimal.class, journalId);
        assertEquals(0, net.signum(), "ledger tidak seimbang");
        Integer mismatched = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounting_ledger_entries WHERE reference_id = ? AND is_cancelled <> ?",
                Integer.class, journalId, cancelled);
        assertEquals(0, mismatched, "status is_cancelled ledger");
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM accounting_ledger_entries WHERE reference_id = ?",
                Integer.class, journalId);
        assertEquals(2, rows);
    }

    static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "diharapkan " + expected + " tetapi " + actual);
    }

    static void assertMoney(String expected, JsonNode node) {
        assertNotNull(node);
        assertMoney(expected, node.isTextual() ? new BigDecimal(node.asText()) : node.decimalValue());
    }

    static java.util.Map<String, Object> body(Object... kv) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
