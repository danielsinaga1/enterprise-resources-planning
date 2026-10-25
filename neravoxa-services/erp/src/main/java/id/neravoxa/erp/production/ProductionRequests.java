package id.neravoxa.erp.production;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO body request produksi — port skema Zod di {@code utils/schemas.ts}
 * (CreateMachineSchema, CreateProductionProcessSchema, CreateProductionCostStandardSchema,
 * CreateProductionRunSchema beserta varian Update). Pesan validasi disalin dari Zod; pesan
 * bawaan Zod 4 dipakai untuk constraint tanpa pesan kustom.
 */
public final class ProductionRequests {

    private ProductionRequests() {
    }

    /** {@code z.string().uuid()} Zod 4 (RFC 9562 + nil/max). */
    public static final String UUID = "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
            + "|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)$";
    static final String INVALID_UUID = "Invalid UUID";
    static final String REQ_STRING = "Invalid input: expected string, received undefined";
    static final String REQ_ARRAY = "Invalid input: expected array, received undefined";
    static final String MIN0 = "Too small: expected number to be >=0";
    static final String INT = "Invalid input: expected int, received number";
    static final String COST_SIDE = "Invalid option: expected one of \"InputSide\"|\"OutputSide\"";
    static final String BASIS = "Invalid option: expected one of \"InputQty\"|\"OutputQty\"|\"Fixed\"";

    // ───────────────────────────── Mesin ─────────────────────────────

    /** CreateMachineSchema. {@code unit} default 'kg' diterapkan di service. */
    public record MachineCreateRequest(
            @NotNull(message = REQ_STRING)
            @Size.List({@Size(min = 1, message = "Kode mesin wajib diisi."),
                    @Size(max = 50, message = "Too big: expected string to have <=50 characters")}) String code,
            @NotNull(message = REQ_STRING)
            @Size.List({@Size(min = 1, message = "Nama mesin wajib diisi."),
                    @Size(max = 150, message = "Too big: expected string to have <=150 characters")}) String name,
            @DecimalMin(value = "0", message = "Kapasitas ideal tidak boleh negatif.") BigDecimal idealRatePerHour,
            @Size(max = 20, message = "Too big: expected string to have <=20 characters") String unit,
            Boolean isActive,
            String notes) {
    }

    /** UpdateMachineSchema = CreateMachineSchema.partial(); kehadiran field dicek dari body mentah. */
    public record MachineUpdateRequest(
            @Size.List({@Size(min = 1, message = "Kode mesin wajib diisi."),
                    @Size(max = 50, message = "Too big: expected string to have <=50 characters")}) String code,
            @Size.List({@Size(min = 1, message = "Nama mesin wajib diisi."),
                    @Size(max = 150, message = "Too big: expected string to have <=150 characters")}) String name,
            @DecimalMin(value = "0", message = "Kapasitas ideal tidak boleh negatif.") BigDecimal idealRatePerHour,
            @Size(max = 20, message = "Too big: expected string to have <=20 characters") String unit,
            Boolean isActive,
            String notes) {
    }

    // ───────────────────────────── Proses produksi ─────────────────────────────

    /** CreateProductionProcessSchema ({@code sortOrder} default 0). */
    public record ProcessCreateRequest(
            @NotNull(message = REQ_STRING)
            @Size.List({@Size(min = 1, message = "Nama proses wajib diisi."),
                    @Size(max = 100, message = "Too big: expected string to have <=100 characters")}) String name,
            String description,
            @Digits(integer = 10, fraction = 0, message = INT) BigDecimal sortOrder) {
    }

    /** UpdateProductionProcessSchema = partial + isActive. */
    public record ProcessUpdateRequest(
            @Size.List({@Size(min = 1, message = "Nama proses wajib diisi."),
                    @Size(max = 100, message = "Too big: expected string to have <=100 characters")}) String name,
            String description,
            @Digits(integer = 10, fraction = 0, message = INT) BigDecimal sortOrder,
            Boolean isActive) {
    }

    // ───────────────────────────── Standar biaya ─────────────────────────────

    /** CreateProductionCostStandardSchema. */
    public record CostStandardCreateRequest(
            @NotNull(message = REQ_STRING) @Size(min = 1, message = "Nama standar biaya wajib diisi.") String name,
            String description,
            @NotNull(message = COST_SIDE) @Pattern(regexp = "InputSide|OutputSide", message = COST_SIDE) String costSide,
            @NotNull(message = BASIS) @Pattern(regexp = "InputQty|OutputQty|Fixed", message = BASIS) String basis,
            @NotNull(message = ErpProductionBodyParser.NAN)
            @DecimalMin(value = "0", message = "Rate tidak boleh negatif.") BigDecimal defaultRate,
            @Pattern(regexp = UUID, message = INVALID_UUID) String costAccountId,
            @Pattern(regexp = UUID, message = INVALID_UUID) String itemId,
            @Pattern(regexp = UUID, message = INVALID_UUID) String warehouseId) {
    }

    /** UpdateProductionCostStandardSchema = partial + isActive. */
    public record CostStandardUpdateRequest(
            @Size(min = 1, message = "Nama standar biaya wajib diisi.") String name,
            String description,
            @Pattern(regexp = "InputSide|OutputSide", message = COST_SIDE) String costSide,
            @Pattern(regexp = "InputQty|OutputQty|Fixed", message = BASIS) String basis,
            @DecimalMin(value = "0", message = "Rate tidak boleh negatif.") BigDecimal defaultRate,
            @Pattern(regexp = UUID, message = INVALID_UUID) String costAccountId,
            @Pattern(regexp = UUID, message = INVALID_UUID) String itemId,
            @Pattern(regexp = UUID, message = INVALID_UUID) String warehouseId,
            Boolean isActive) {
    }

    // ───────────────────────────── Data produksi ─────────────────────────────

    /** Baris bahan baku (ProductionInputLineSchema); batch opsional mengunci harga saat dipilih. */
    public record InputLine(
            @NotNull(message = REQ_STRING) @Size(min = 1, message = "Item wajib dipilih.") String itemId,
            @NotNull(message = ErpProductionBodyParser.NAN) @Positive(message = "Kuantitas harus lebih dari 0.") BigDecimal quantity,
            @Pattern(regexp = UUID, message = INVALID_UUID) String sourceMovementId,
            @DecimalMin(value = "0", message = MIN0) BigDecimal batchUnitCost) {
    }

    /** Baris hasil (ProductionOutputLineSchema); harga produk utama boleh kosong (diisi HPP loaded). */
    public record OutputLine(
            @NotNull(message = REQ_STRING) @Size(min = 1, message = "Item wajib dipilih.") String itemId,
            @NotNull(message = ErpProductionBodyParser.NAN) @Positive(message = "Kuantitas harus lebih dari 0.") BigDecimal quantity,
            @DecimalMin(value = "0", message = "Harga tidak boleh negatif.") BigDecimal unitPrice,
            Boolean isByProduct) {

        public OutputLine {
            isByProduct = isByProduct != null && isByProduct;
        }

        /** {@code o.isByProduct} (default false). */
        public boolean byProduct() {
            return Boolean.TRUE.equals(isByProduct);
        }
    }

    /** Komponen biaya (ProductionRunCostInputSchema): server menghitung qtyBasis + totalAmount. */
    public record CostLine(
            @Pattern(regexp = UUID, message = INVALID_UUID) String standardId,
            @NotNull(message = REQ_STRING) @Size(min = 1, message = "Keterangan biaya wajib diisi.") String description,
            @NotNull(message = COST_SIDE) @Pattern(regexp = "InputSide|OutputSide", message = COST_SIDE) String costSide,
            @NotNull(message = BASIS) @Pattern(regexp = "InputQty|OutputQty|Fixed", message = BASIS) String basis,
            @NotNull(message = ErpProductionBodyParser.NAN) @DecimalMin(value = "0", message = "Rate tidak boleh negatif.") BigDecimal rate,
            @Pattern(regexp = UUID, message = INVALID_UUID) String costAccountId) {
    }

    /** Downtime OEE (ProductionDowntimeLineSchema). */
    public record DowntimeLine(
            @Pattern(regexp = UUID, message = "Mesin downtime tidak valid.") String machineId,
            @NotNull(message = REQ_STRING)
            @Size.List({@Size(min = 1, message = "Kategori downtime wajib diisi."),
                    @Size(max = 50, message = "Too big: expected string to have <=50 characters")}) String category,
            @Size(max = 500, message = "Too big: expected string to have <=500 characters") String reason,
            @NotNull(message = ErpProductionBodyParser.NAN) @DecimalMin(value = "0", message = "Durasi tidak boleh negatif.") BigDecimal durationMin) {
    }

    /** Mesin paralel dalam run (ProductionRunMachineLineSchema). */
    public record MachineLine(
            @NotNull(message = REQ_STRING) @Pattern(regexp = UUID, message = "Mesin tidak valid.") String machineId,
            @JsDate String startAt,
            @JsDate String endAt) {
    }

    /**
     * CreateProductionRunSchema (= UpdateProductionRunSchema). Default Zod: costs/processIds/
     * runMachines/downtimes = [], allowNegativeStock = false. superRefine dicek di
     * {@link ErpProductionBodyParser#parseProductionRun}.
     */
    public record ProductionRunRequest(
            @NotNull(message = REQ_STRING) @Size(min = 1, message = "Tanggal wajib diisi.") String date,
            @Pattern(regexp = UUID, message = "Gudang wajib dipilih.") String warehouseId,
            String notes,
            String referenceType,
            String referenceId,
            String referenceNumber,
            @NotNull(message = REQ_ARRAY) @Size(min = 1, message = "Minimal satu item input.") List<@Valid InputLine> inputs,
            @NotNull(message = REQ_ARRAY) @Size(min = 1, message = "Minimal satu item output.") List<@Valid OutputLine> outputs,
            List<@Valid CostLine> costs,
            @DecimalMin(value = "0", message = MIN0) BigDecimal hppOverride,
            List<@Pattern(regexp = UUID, message = INVALID_UUID) String> processIds,
            @JsDate String plannedStart,
            @JsDate String plannedEnd,
            @JsDate String breakStart,
            @JsDate String breakEnd,
            List<@Valid MachineLine> runMachines,
            @Size(max = 20, message = "Too big: expected string to have <=20 characters") String shift,
            @Size(max = 150, message = "Too big: expected string to have <=150 characters") String shiftLead,
            @Digits(integer = 9, fraction = 0, message = INT) @DecimalMin(value = "0", message = MIN0) BigDecimal manpowerCount,
            @DecimalMin(value = "0", message = MIN0) BigDecimal goodQty,
            @DecimalMin(value = "0", message = MIN0) BigDecimal rejectQty,
            List<@Valid DowntimeLine> downtimes,
            @Pattern(regexp = UUID, message = "Mesin tidak valid.") String machineId,
            @JsDate String startAt,
            @JsDate String endAt,
            Boolean allowNegativeStock,
            @Size(max = 500, message = "Too big: expected string to have <=500 characters") String negativeStockReason) {

        public ProductionRunRequest {
            costs = costs == null ? List.of() : costs;
            processIds = processIds == null ? List.of() : processIds;
            runMachines = runMachines == null ? List.of() : runMachines;
            downtimes = downtimes == null ? List.of() : downtimes;
            allowNegativeStock = allowNegativeStock != null && allowNegativeStock;
        }

        /** {@code body.allowNegativeStock} (default false). */
        public boolean negativeStock() {
            return Boolean.TRUE.equals(allowNegativeStock);
        }
    }
}
