package id.enterpriseapps.erp.cashbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.entity.PaymentType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static id.enterpriseapps.erp.cashbank.CashBankZod.enumValue;
import static id.enterpriseapps.erp.cashbank.CashBankZod.field;
import static id.enterpriseapps.erp.cashbank.CashBankZod.nonNegativeDefaultZero;
import static id.enterpriseapps.erp.cashbank.CashBankZod.number;
import static id.enterpriseapps.erp.cashbank.CashBankZod.optionalArray;
import static id.enterpriseapps.erp.cashbank.CashBankZod.optionalBoolean;
import static id.enterpriseapps.erp.cashbank.CashBankZod.optionalMinString;
import static id.enterpriseapps.erp.cashbank.CashBankZod.optionalString;
import static id.enterpriseapps.erp.cashbank.CashBankZod.optionalUuid;
import static id.enterpriseapps.erp.cashbank.CashBankZod.positive;
import static id.enterpriseapps.erp.cashbank.CashBankZod.requiredArray;
import static id.enterpriseapps.erp.cashbank.CashBankZod.requiredString;
import static id.enterpriseapps.erp.cashbank.CashBankZod.requiredUuid;

/**
 * Body request kas &amp; bank — port skema Zod {@code CreatePaymentSchema}, {@code CreateReconciliationSchema},
 * {@code AddStatementItemsSchema}, {@code MatchItemSchema}, {@code UnmatchItemSchema},
 * {@code CreateProjectGuaranteeSchema}, {@code SettleProjectGuaranteeSchema} (utils/schemas.ts).
 * Field diperiksa sesuai urutan skema; error pertama dikembalikan 400 {@code { error }}.
 */
public final class CashBankRequests {

    /** Jenis uang jaminan ({@code GUARANTEE_TYPES}). */
    public static final List<String> GUARANTEE_TYPES = List.of("Penawaran", "Pelaksanaan", "Pemeliharaan", "Lainnya");

    private static final List<String> PAYMENT_TYPES = Arrays.stream(PaymentType.values()).map(Enum::name).toList();
    private static final List<String> ALLOCATION_TYPES = List.of("SalesInvoice", "PurchaseInvoice");

    private CashBankRequests() {
    }

    // ───────────────────────────── pembayaran ─────────────────────────────

    /** Satu baris split akun (multi-akun: kas, bank, potongan beban, dst.). */
    public record PaymentSplitInput(String accountId, BigDecimal amount, String notes) {
    }

    /** Port {@code CreatePaymentSchema}. {@code allocations} divalidasi tetapi tidak dipakai (sama dengan sumber). */
    public record CreatePayment(String date, String partyId, BigDecimal amount, PaymentType paymentType,
                                String accountId, List<PaymentSplitInput> splits, String referenceNo, String notes,
                                Boolean isOpeningBalance, String refundSourcePaymentId, String salesOrderId) {

        static CreatePayment parse(JsonNode raw) {
            ObjectNode b = CashBankZod.object(raw);
            String date = requiredString(field(b, "date"), "Tanggal wajib diisi.");
            String partyId = requiredString(field(b, "partyId"), "Pihak wajib dipilih.");
            BigDecimal amount = positive(field(b, "amount"), "Jumlah harus lebih dari 0.");
            PaymentType type = PaymentType.valueOf(enumValue(field(b, "paymentType"), PAYMENT_TYPES));
            String accountId = requiredString(field(b, "accountId"), "Akun kas/bank wajib dipilih.");
            List<JsonNode> allocations = optionalArray(field(b, "allocations"));
            if (allocations != null) {
                for (JsonNode a : allocations) {
                    if (a == null || !a.isObject()) {
                        throw ErpException.business(CashBankZod.invalidType("object", a));
                    }
                    enumValue(field(a, "invoiceType"), ALLOCATION_TYPES);
                    requiredString(field(a, "invoiceId"), CashBankZod.STRING_MIN_1);
                    positive(field(a, "amount"), null);
                }
            }
            List<JsonNode> rawSplits = optionalArray(field(b, "splits"));
            List<PaymentSplitInput> splits = null;
            if (rawSplits != null) {
                splits = new ArrayList<>();
                for (JsonNode s : rawSplits) {
                    if (s == null || !s.isObject()) {
                        throw ErpException.business(CashBankZod.invalidType("object", s));
                    }
                    String splitAccount = requiredString(field(s, "accountId"), "Akun split wajib dipilih.");
                    BigDecimal splitAmount = positive(field(s, "amount"), "Jumlah split harus lebih dari 0.");
                    String splitNotes = optionalString(field(s, "notes"));
                    splits.add(new PaymentSplitInput(splitAccount, splitAmount, splitNotes));
                }
            }
            String referenceNo = optionalString(field(b, "referenceNo"));
            String notes = optionalString(field(b, "notes"));
            Boolean opening = optionalBoolean(field(b, "isOpeningBalance"));
            String refundSource = optionalMinString(field(b, "refundSourcePaymentId"));
            String salesOrderId = optionalUuid(field(b, "salesOrderId"));
            return new CreatePayment(date, partyId, amount, type, accountId, splits, referenceNo, notes, opening,
                    refundSource, salesOrderId);
        }
    }

    // ───────────────────────────── rekonsiliasi ─────────────────────────────

    /** Port {@code CreateReconciliationSchema}. */
    public record CreateReconciliation(String accountId, String statementDate, BigDecimal statementBalance,
                                       String notes) {

        static CreateReconciliation parse(JsonNode raw) {
            ObjectNode b = CashBankZod.object(raw);
            String accountId = requiredString(field(b, "accountId"), "Akun bank wajib dipilih.");
            String statementDate = requiredString(field(b, "statementDate"), "Tanggal statement wajib diisi.");
            BigDecimal balance = number(field(b, "statementBalance"), "Saldo statement harus berupa angka.");
            String notes = optionalString(field(b, "notes"));
            return new CreateReconciliation(accountId, statementDate, balance, notes);
        }
    }

    /** Satu baris rekening koran. */
    public record StatementItemInput(BigDecimal statementAmount, String statementDesc, String statementDate) {
    }

    /** Port {@code AddStatementItemsSchema}. */
    public record AddStatementItems(List<StatementItemInput> items) {

        static AddStatementItems parse(JsonNode raw) {
            ObjectNode b = CashBankZod.object(raw);
            List<JsonNode> rows = requiredArray(field(b, "items"));
            List<StatementItemInput> items = new ArrayList<>();
            for (JsonNode r : rows) {
                if (r == null || !r.isObject()) {
                    throw ErpException.business(CashBankZod.invalidType("object", r));
                }
                BigDecimal amount = number(field(r, "statementAmount"), "Jumlah harus berupa angka.");
                String desc = optionalString(field(r, "statementDesc"));
                String date = optionalString(field(r, "statementDate"));
                items.add(new StatementItemInput(amount, desc, date));
            }
            // Cek .min(1) array dijalankan setelah elemen (urutan issue Zod: elemen dulu, lalu panjang).
            if (items.isEmpty()) {
                throw ErpException.business("Minimal satu item statement.");
            }
            return new AddStatementItems(items);
        }
    }

    /** Port {@code MatchItemSchema}. */
    public record MatchItem(String itemId, String ledgerEntryId) {

        static MatchItem parse(JsonNode raw) {
            ObjectNode b = CashBankZod.object(raw);
            String itemId = requiredString(field(b, "itemId"), "ID item wajib diisi.");
            String ledgerEntryId = requiredString(field(b, "ledgerEntryId"), "ID ledger entry wajib diisi.");
            return new MatchItem(itemId, ledgerEntryId);
        }
    }

    /** Port {@code UnmatchItemSchema}. */
    public record UnmatchItem(String itemId) {

        static UnmatchItem parse(JsonNode raw) {
            ObjectNode b = CashBankZod.object(raw);
            return new UnmatchItem(requiredString(field(b, "itemId"), "ID item wajib diisi."));
        }
    }

    // ───────────────────────────── uang jaminan proyek ─────────────────────────────

    /** Port {@code CreateProjectGuaranteeSchema}. */
    public record CreateGuarantee(String date, String partyId, String guaranteeType, String projectName,
                                  String referenceNo, BigDecimal amount, String accountId,
                                  String expectedReturnDate, String notes) {

        static CreateGuarantee parse(JsonNode raw) {
            ObjectNode b = CashBankZod.object(raw);
            String date = requiredString(field(b, "date"), "Tanggal wajib diisi.");
            String partyId = requiredUuid(field(b, "partyId"), "Pihak pemberi proyek wajib dipilih.");
            String type = enumValue(field(b, "guaranteeType"), GUARANTEE_TYPES);
            String projectName = requiredString(field(b, "projectName"), "Nama proyek wajib diisi.");
            String referenceNo = optionalString(field(b, "referenceNo"));
            BigDecimal amount = positive(field(b, "amount"), "Jumlah jaminan harus lebih dari 0.");
            String accountId = requiredUuid(field(b, "accountId"), "Akun sumber dana wajib dipilih.");
            String expected = optionalString(field(b, "expectedReturnDate"));
            String notes = optionalString(field(b, "notes"));
            return new CreateGuarantee(date, partyId, type, projectName, referenceNo, amount, accountId, expected, notes);
        }
    }

    /** Port {@code SettleProjectGuaranteeSchema} (termasuk dua refine-nya). */
    public record SettleGuarantee(String date, BigDecimal returnAmount, BigDecimal forfeitAmount,
                                  String returnAccountId, String expenseAccountId, String notes) {

        static SettleGuarantee parse(JsonNode raw) {
            ObjectNode b = CashBankZod.object(raw);
            String date = requiredString(field(b, "date"), "Tanggal wajib diisi.");
            BigDecimal returnAmount = nonNegativeDefaultZero(field(b, "returnAmount"));
            BigDecimal forfeitAmount = nonNegativeDefaultZero(field(b, "forfeitAmount"));
            String returnAccountId = optionalUuid(field(b, "returnAccountId"));
            String expenseAccountId = optionalUuid(field(b, "expenseAccountId"));
            String notes = optionalString(field(b, "notes"));
            if (returnAmount.add(forfeitAmount).signum() <= 0) {
                throw ErpException.business("Masukkan jumlah dikembalikan atau hangus minimal salah satu.");
            }
            if (returnAmount.signum() != 0 && (returnAccountId == null || returnAccountId.isEmpty())) {
                throw ErpException.business("Pilih akun kas/bank tujuan pengembalian.");
            }
            return new SettleGuarantee(date, returnAmount, forfeitAmount, returnAccountId, expenseAccountId, notes);
        }
    }
}
