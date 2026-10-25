package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body request slice akuntansi — port skema Zod di {@code utils/schemas.ts} (pesan disalin persis).
 *
 * <p>Field {@code z.coerce.number()} bertipe {@link JsonNode} dan divalidasi di service lewat
 * {@link ZodLite} (koersi {@code Number(x)} tidak bisa dinyatakan dengan anotasi). Field yang
 * semantik "absen vs null"-nya penting pada update parsial juga {@link JsonNode}
 * (absen = Java null, {@code null} JSON = NullNode).
 */
public final class AccountingRequests {

    static final String ACCOUNT_TYPE_RE = "ASSET|LIABILITY|EQUITY|REVENUE|EXPENSE";
    static final String ACCOUNT_TYPE_MSG = "Tipe akun harus salah satu dari: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE";
    static final String TAX_TYPE_RE = "sales|purchase|withholding";
    static final String TAX_TYPE_MSG = "Tipe harus: sales, purchase, atau withholding.";
    static final String TEMPLATE_TYPE_RE = "journal|sales_invoice|purchase_invoice";
    static final String TEMPLATE_TYPE_MSG = "Invalid option: expected one of \"journal\"|\"sales_invoice\"|\"purchase_invoice\"";
    static final String FREQUENCY_RE = "daily|weekly|monthly|quarterly|yearly";
    static final String FREQUENCY_MSG =
            "Invalid option: expected one of \"daily\"|\"weekly\"|\"monthly\"|\"quarterly\"|\"yearly\"";

    private AccountingRequests() {
    }

    /** {@code CreateAccountSchema}. {@code currency} diterima tetapi tidak dipakai (sama dengan sumber). */
    public record CoaCreate(
            @NotEmpty(message = "Nomor akun wajib diisi.") String accountNumber,
            @NotEmpty(message = "Nama akun wajib diisi.") String name,
            @NotNull(message = ACCOUNT_TYPE_MSG) @Pattern(regexp = ACCOUNT_TYPE_RE, message = ACCOUNT_TYPE_MSG) String accountType,
            @NotNull(message = ACCOUNT_TYPE_MSG) @Pattern(regexp = ACCOUNT_TYPE_RE, message = ACCOUNT_TYPE_MSG) String rootType,
            String parentId,
            Boolean isGroup,
            String currency) {
    }

    /** {@code UpdateAccountSchema} = CreateAccountSchema.partial() + isActive. */
    public record CoaUpdate(
            @Size(min = 1, message = "Nomor akun wajib diisi.") String accountNumber,
            @Size(min = 1, message = "Nama akun wajib diisi.") String name,
            @Pattern(regexp = ACCOUNT_TYPE_RE, message = ACCOUNT_TYPE_MSG) String accountType,
            @Pattern(regexp = ACCOUNT_TYPE_RE, message = ACCOUNT_TYPE_MSG) String rootType,
            String parentId,
            Boolean isGroup,
            String currency,
            Boolean isActive) {
    }

    /** {@code SetBalanceSchema}: balance = z.coerce.number({ error: 'Saldo harus berupa angka.' }). */
    public record CoaBalance(JsonNode balance) {
    }

    /** {@code CreateJournalSchema}. */
    public record JournalCreate(
            @NotEmpty(message = "Tanggal wajib diisi.") String date,
            @NotEmpty(message = "Keterangan wajib diisi.") String narration,
            @NotEmpty(message = "Minimal satu baris jurnal.") List<@Valid JournalLine> items) {
    }

    /** {@code JournalItemSchema}: debit/credit = z.coerce.number().min(0). */
    public record JournalLine(
            @NotEmpty(message = "Akun wajib dipilih.") String accountId,
            JsonNode debit,
            JsonNode credit,
            String partyId,
            String description) {
    }

    /** {@code CreateFiscalYearSchema}. */
    public record FiscalYearCreate(
            @NotEmpty(message = "Nama tahun fiskal wajib diisi.") String name,
            @NotEmpty(message = "Tanggal mulai wajib diisi.") String startDate,
            @NotEmpty(message = "Tanggal selesai wajib diisi.") String endDate) {
    }

    /** {@code CreateTaxConfigSchema}; rate = z.coerce.number().min(0).max(100). */
    public record TaxConfigCreate(
            @NotEmpty(message = "Nama pajak wajib diisi.") String name,
            JsonNode rate,
            @NotNull(message = TAX_TYPE_MSG) @Pattern(regexp = TAX_TYPE_RE, message = TAX_TYPE_MSG) String type,
            String accountId) {
    }

    /** {@code UpdateTaxConfigSchema} = CreateTaxConfigSchema.partial() + isActive. */
    public record TaxConfigUpdate(
            @Size(min = 1, message = "Nama pajak wajib diisi.") String name,
            JsonNode rate,
            @Pattern(regexp = TAX_TYPE_RE, message = TAX_TYPE_MSG) String type,
            JsonNode accountId,
            Boolean isActive) {
    }

    /** {@code CreateRecurringSchema}; templateData = union(journal, invoice) — lihat {@link RecurringTemplateData}. */
    public record RecurringCreate(
            @NotEmpty(message = "Nama template wajib diisi.") String name,
            @NotNull(message = TEMPLATE_TYPE_MSG) @Pattern(regexp = TEMPLATE_TYPE_RE, message = TEMPLATE_TYPE_MSG) String templateType,
            @NotNull(message = FREQUENCY_MSG) @Pattern(regexp = FREQUENCY_RE, message = FREQUENCY_MSG) String frequency,
            JsonNode dayOfMonth,
            @NotEmpty(message = "Tanggal berikutnya wajib diisi.") String nextRunDate,
            JsonNode templateData) {
    }

    /** {@code UpdateRecurringSchema}. */
    public record RecurringUpdate(
            @Size(min = 1, message = "Nama template wajib diisi.") String name,
            @Pattern(regexp = TEMPLATE_TYPE_RE, message = TEMPLATE_TYPE_MSG) String templateType,
            @Pattern(regexp = FREQUENCY_RE, message = FREQUENCY_MSG) String frequency,
            JsonNode dayOfMonth,
            String nextRunDate,
            JsonNode templateData,
            Boolean isActive) {
    }
}
