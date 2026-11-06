package id.neravoxa.erp.toll;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Body request modul Jasa Maklon (validasi manual; pesan Bahasa Indonesia, error pertama → 400). */
public final class TollRequests {

    private TollRequests() {
    }

    static final Set<String> BASES = Set.of("InputQty", "OutputQty", "Flat");
    static final Set<String> OWNERS = Set.of("Customer", "Factory");

    private static <T> T bind(JsonNode raw, Class<T> type) {
        if (raw == null || raw.isNull() || !raw.isObject()) {
            throw ErpException.business("Body request tidak valid.");
        }
        try {
            return ErpJson.convert(raw, type);
        } catch (IllegalArgumentException e) {
            throw ErpException.business("Format data tidak valid: periksa angka dan tanggal.");
        }
    }

    private static void requireDate(String date, String label) {
        if (!TollSql.has(date)) {
            throw ErpException.business(label + " wajib diisi.");
        }
        if (ErpDates.tryInstant(date) == null) {
            throw ErpException.business("Format tanggal tidak valid.");
        }
    }

    private static void nonNegative(BigDecimal v, String label) {
        if (v != null && v.signum() < 0) {
            throw ErpException.business(label + " tidak boleh negatif.");
        }
    }

    /** POST/PUT /toll/orders. Field tarif kosong = diisi dari tarif pelanggan / item jasa. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderInput(String date, String partyId, String serviceItemId, String pricingBasis, BigDecimal rate,
                             BigDecimal minimumCharge, BigDecimal taxPct, String byProductOwner, BigDecimal plannedQty,
                             BigDecimal expectedYieldPct, BigDecimal storageRate, Integer storageFreeDays, String notes) {

        public static OrderInput parse(JsonNode raw) {
            OrderInput b = bind(raw, OrderInput.class);
            requireDate(b.date(), "Tanggal");
            if (!TollSql.has(b.partyId())) {
                throw ErpException.business("Pelanggan wajib dipilih.");
            }
            if (TollSql.has(b.pricingBasis()) && !BASES.contains(b.pricingBasis())) {
                throw ErpException.business("Dasar tarif harus InputQty, OutputQty, atau Flat.");
            }
            if (TollSql.has(b.byProductOwner()) && !OWNERS.contains(b.byProductOwner())) {
                throw ErpException.business("Pemilik produk samping harus Customer atau Factory.");
            }
            nonNegative(b.rate(), "Tarif");
            nonNegative(b.minimumCharge(), "Ongkos minimum");
            nonNegative(b.plannedQty(), "Rencana kuantitas");
            nonNegative(b.storageRate(), "Tarif simpan");
            if (b.storageFreeDays() != null && b.storageFreeDays() < 0) {
                throw ErpException.business("Masa bebas simpan tidak boleh negatif.");
            }
            if (b.taxPct() != null && (b.taxPct().signum() < 0 || b.taxPct().compareTo(BigDecimal.valueOf(100)) > 0)) {
                throw ErpException.business("PPN harus 0–100%.");
            }
            if (b.expectedYieldPct() != null
                    && (b.expectedYieldPct().signum() < 0 || b.expectedYieldPct().compareTo(BigDecimal.valueOf(100)) > 0)) {
                throw ErpException.business("Target rendemen harus 0–100%.");
            }
            if (b.notes() != null && b.notes().length() > 2000) {
                throw ErpException.business("Catatan maksimal 2000 karakter.");
            }
            return b;
        }
    }

    /** POST /toll/orders/:id/invoice. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvoiceInput(String date, String dueDate, String notes, Boolean includeStorage, Boolean includeMinimum) {

        public static InvoiceInput parse(JsonNode raw) {
            InvoiceInput b = bind(raw == null || raw.isNull() ? ErpJson.object() : raw, InvoiceInput.class);
            requireDate(b.date(), "Tanggal invoice");
            if (TollSql.has(b.dueDate()) && ErpDates.tryInstant(b.dueDate()) == null) {
                throw ErpException.business("Tanggal jatuh tempo tidak valid.");
            }
            return b;
        }

        boolean storage() {
            return includeStorage == null || includeStorage;
        }

        boolean minimum() {
            return includeMinimum == null || includeMinimum;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReturnLine(String itemId, String warehouseId, String itemName, BigDecimal quantity, String unit) {
    }

    /** POST /toll/returns — Surat Jalan Pengembalian Titipan. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReturnInput(String date, String partyId, String tollOrderId, String nomorMobil, String driverName,
                              String recipientName, String notes, List<ReturnLine> items) {

        public static ReturnInput parse(JsonNode raw) {
            ReturnInput b = bind(raw, ReturnInput.class);
            requireDate(b.date(), "Tanggal");
            if (!TollSql.has(b.partyId())) {
                throw ErpException.business("Pelanggan wajib dipilih.");
            }
            if (b.items() == null || b.items().isEmpty()) {
                throw ErpException.business("Minimal satu barang yang dikembalikan.");
            }
            for (ReturnLine l : b.items()) {
                if (l == null || !TollSql.has(l.itemId())) {
                    throw ErpException.business("Item wajib dipilih di setiap baris.");
                }
                if (!TollSql.has(l.warehouseId())) {
                    throw ErpException.business("Gudang wajib dipilih di setiap baris.");
                }
                if (l.quantity() == null || l.quantity().signum() <= 0) {
                    throw ErpException.business("Jumlah harus lebih dari 0.");
                }
            }
            return b;
        }
    }

    /** POST /toll/stock/transfer — pindah stok titipan antar gudang. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransferInput(String date, String partyId, String itemId, String fromWarehouseId, String toWarehouseId,
                                BigDecimal quantity, String tollOrderId, String notes) {

        public static TransferInput parse(JsonNode raw) {
            TransferInput b = bind(raw, TransferInput.class);
            requireDate(b.date(), "Tanggal");
            if (!TollSql.has(b.partyId())) {
                throw ErpException.business("Pelanggan wajib dipilih.");
            }
            if (!TollSql.has(b.itemId())) {
                throw ErpException.business("Item wajib dipilih.");
            }
            if (!TollSql.has(b.fromWarehouseId()) || !TollSql.has(b.toWarehouseId())) {
                throw ErpException.business("Gudang asal dan tujuan wajib dipilih.");
            }
            if (b.fromWarehouseId().equals(b.toWarehouseId())) {
                throw ErpException.business("Gudang asal dan tujuan tidak boleh sama.");
            }
            if (b.quantity() == null || b.quantity().signum() <= 0) {
                throw ErpException.business("Jumlah harus lebih dari 0.");
            }
            return b;
        }
    }

    /** POST /toll/rates — tarif jasa khusus pelanggan (upsert per item jasa × pelanggan). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RateInput(String serviceItemId, String partyId, BigDecimal rate, BigDecimal minimumCharge, String notes) {

        public static RateInput parse(JsonNode raw) {
            RateInput b = bind(raw, RateInput.class);
            if (!TollSql.has(b.serviceItemId())) {
                throw ErpException.business("Item jasa wajib dipilih.");
            }
            if (!TollSql.has(b.partyId())) {
                throw ErpException.business("Pelanggan wajib dipilih.");
            }
            if (b.rate() == null) {
                throw ErpException.business("Tarif wajib diisi.");
            }
            nonNegative(b.rate(), "Tarif");
            nonNegative(b.minimumCharge(), "Ongkos minimum");
            return b;
        }
    }
}
