package id.neravoxa.erp.sales;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/** Body request layanan/jasa (port schema Zod di utils/schemas.ts). */
public final class ServiceItemRequests {

    private ServiceItemRequests() {
    }

    /** Port {@code CreateServiceItemSchema}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Create(
            @NotEmpty(message = "Kode layanan wajib diisi.")
            String code,
            @NotEmpty(message = "Nama layanan wajib diisi.")
            String name,
            String unit,
            @DecimalMin(value = "0", message = ZodText.NUMBER_GTE_0)
            BigDecimal defaultRate,
            @NotNull(message = "Akun pendapatan wajib dipilih.")
            @Pattern(regexp = ZodText.UUID_REGEX, message = "Akun pendapatan wajib dipilih.")
            String accountId,
            String description,
            /** Dasar tarif default order jasa maklon: InputQty | OutputQty | Flat (kosong = qty manual). */
            @Pattern(regexp = "InputQty|OutputQty|Flat", message = "Dasar tarif harus InputQty, OutputQty, atau Flat.")
            String pricingBasis,
            @DecimalMin(value = "0", message = ZodText.NUMBER_GTE_0)
            BigDecimal minimumCharge) {
    }

    /**
     * Port {@code UpdateServiceItemSchema} (= Create.partial() + isActive). Hanya field yang dikirim
     * yang diubah (Prisma {@code data: body}); kehadiran field dicatat lewat setter.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Patch implements ErpSalesRequestValidator.Refinable {

        @Size(min = 1, message = "Kode layanan wajib diisi.")
        private String code;
        @Size(min = 1, message = "Nama layanan wajib diisi.")
        private String name;
        private String unit;
        @DecimalMin(value = "0", message = ZodText.NUMBER_GTE_0)
        private BigDecimal defaultRate;
        @Pattern(regexp = ZodText.UUID_REGEX, message = "Akun pendapatan wajib dipilih.")
        private String accountId;
        private String description;
        private Boolean isActive;
        @Pattern(regexp = "InputQty|OutputQty|Flat", message = "Dasar tarif harus InputQty, OutputQty, atau Flat.")
        private String pricingBasis;
        @DecimalMin(value = "0", message = ZodText.NUMBER_GTE_0)
        private BigDecimal minimumCharge;
        private final transient Set<String> present = new HashSet<>();

        public void setPricingBasis(String v) {
            pricingBasis = v == null || v.isBlank() ? null : v;
            present.add("pricingBasis");
        }

        public void setMinimumCharge(BigDecimal v) {
            minimumCharge = v;
            present.add("minimumCharge");
        }

        String pricingBasis() {
            return pricingBasis;
        }

        BigDecimal minimumCharge() {
            return minimumCharge;
        }

        public void setCode(String v) {
            code = v;
            present.add("code");
        }

        public void setName(String v) {
            name = v;
            present.add("name");
        }

        public void setUnit(String v) {
            unit = v;
            present.add("unit");
        }

        public void setDefaultRate(BigDecimal v) {
            defaultRate = v;
            present.add("defaultRate");
        }

        public void setAccountId(String v) {
            accountId = v;
            present.add("accountId");
        }

        public void setDescription(String v) {
            description = v;
            present.add("description");
        }

        public void setIsActive(Boolean v) {
            isActive = v;
            present.add("isActive");
        }

        boolean has(String field) {
            return present.contains(field);
        }

        String code() {
            return code;
        }

        String name() {
            return name;
        }

        String unit() {
            return unit;
        }

        BigDecimal defaultRate() {
            return defaultRate;
        }

        String accountId() {
            return accountId;
        }

        String description() {
            return description;
        }

        Boolean isActive() {
            return isActive;
        }

        /** Field non-nullable yang dikirim null ditolak seperti Zod (invalid_type). */
        @Override
        public String refineError() {
            for (String f : new String[]{"code", "name", "unit", "accountId"}) {
                if (present.contains(f) && value(f) == null) {
                    return ZodText.EXPECTED_STRING_NULL;
                }
            }
            if (present.contains("isActive") && isActive == null) {
                return ZodText.EXPECTED_BOOLEAN_NULL;
            }
            return null;
        }

        private Object value(String f) {
            return switch (f) {
                case "code" -> code;
                case "name" -> name;
                case "unit" -> unit;
                default -> accountId;
            };
        }
    }
}
