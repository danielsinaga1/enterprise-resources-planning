package id.enterpriseapps.erp.deposit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body request uang muka — port schema Zod {@code ApplyCustomerDepositSchema},
 * {@code ApplyVendorDepositSchema}, {@code RefundCustomerDepositSchema}, {@code RefundVendorDepositSchema}
 * (utils/schemas.ts). Urutan komponen = urutan field Zod, karena {@link ErpDepositRequestValidator}
 * mengembalikan issue pertama seperti {@code validateBody}.
 */
final class DepositRequests {

    private DepositRequests() {
    }

    /** Pesan bawaan Zod 4 (locale en) untuk aturan tanpa pesan khusus — disalin persis. */
    static final String EXPECTED_STRING = "Invalid input: expected string, received undefined";
    static final String EXPECTED_NUMBER = "Invalid input: expected number, received NaN";
    static final String INVALID_UUID = "Invalid UUID";
    static final String NUMBER_GTE_0 = "Too small: expected number to be >=0";

    /** Regex {@code z.string().uuid()} Zod 4 (RFC 9562 + nil/max UUID). */
    static final String UUID_REGEX = "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
            + "|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)$";

    /** POST /customer-deposits/apply. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApplyCustomer(
            @NotNull(message = EXPECTED_STRING)
            @Pattern(regexp = UUID_REGEX, message = "ID deposit harus UUID yang valid.")
            String depositPaymentId,
            @NotNull(message = EXPECTED_STRING)
            @Pattern(regexp = UUID_REGEX, message = "ID invoice harus UUID yang valid.")
            String salesInvoiceId,
            @NotNull(message = EXPECTED_NUMBER)
            @Positive(message = "Jumlah harus lebih dari 0.")
            BigDecimal amount) {
    }

    /** POST /vendor-deposits/apply. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApplyVendor(
            @NotNull(message = EXPECTED_STRING)
            @Pattern(regexp = UUID_REGEX, message = "ID deposit harus UUID yang valid.")
            String depositPaymentId,
            @NotNull(message = EXPECTED_STRING)
            @Pattern(regexp = UUID_REGEX, message = "ID invoice harus UUID yang valid.")
            String purchaseInvoiceId,
            @NotNull(message = EXPECTED_NUMBER)
            @Positive(message = "Jumlah harus lebih dari 0.")
            BigDecimal amount) {
    }

    /**
     * POST /:id/refund — selesaikan sisa uang muka lewat offset piutang/hutang (FIFO) dan/atau refund kas.
     * {@code offsetAmount}/{@code cashAmount} default 0 ({@code .default(0)}); refinement dicek di validator.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Refund(
            @NotNull(message = EXPECTED_STRING)
            @Size(min = 1, message = "Tanggal wajib diisi.")
            String date,
            @DecimalMin(value = "0", message = NUMBER_GTE_0)
            BigDecimal offsetAmount,
            @DecimalMin(value = "0", message = NUMBER_GTE_0)
            BigDecimal cashAmount,
            @Pattern(regexp = UUID_REGEX, message = INVALID_UUID)
            String cashAccountId,
            String notes) {

        BigDecimal offset() {
            return offsetAmount == null ? BigDecimal.ZERO : offsetAmount;
        }

        BigDecimal cash() {
            return cashAmount == null ? BigDecimal.ZERO : cashAmount;
        }
    }
}
