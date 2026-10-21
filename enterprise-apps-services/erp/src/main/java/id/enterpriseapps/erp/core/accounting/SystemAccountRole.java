package id.enterpriseapps.erp.core.accounting;

import id.enterpriseapps.erp.entity.RootType;

import java.util.Arrays;
import java.util.List;

/**
 * Peran akun sistem untuk posting GL otomatis — port {@code types/systemAccounts.ts}.
 * Nomor akun bisa dipetakan ulang di Pengaturan → Akun Sistem tanpa mengubah kode.
 */
public enum SystemAccountRole {
    CASH("Kas & Bank", "Akun kas dan bank untuk pembayaran masuk/keluar", true, true, RootType.ASSET, "1.1.1", "1.1.2", "1.1.3", "1.1.4", "1.1.5"),
    AR("Piutang Usaha", "Auto-debit saat faktur penjualan di-submit", false, true, RootType.ASSET, "1.2.1"),
    AP("Hutang Usaha", "Auto-credit saat faktur pembelian di-submit", false, true, RootType.LIABILITY, "2.1.1"),
    INVENTORY("Persediaan", "Akun persediaan barang default", false, true, RootType.ASSET, "1.4.0"),
    SALES("Penjualan", "Auto-credit pendapatan penjualan", false, true, RootType.REVENUE, "4.1"),
    SERVICE_REVENUE("Pendapatan Jasa", "Default akun pendapatan jasa", false, false, RootType.REVENUE, "4.2"),
    VENDOR_DEPOSIT("Uang Muka Pembelian", "Akun uang muka ke vendor/supplier", false, true, RootType.ASSET, "1.3"),
    CUSTOMER_DEPOSIT("Uang Muka Pelanggan", "Akun uang muka dari pelanggan", false, true, RootType.LIABILITY, "2.1.2"),
    PROJECT_GUARANTEE("Uang Jaminan Proyek", "Setoran jaminan (tender/pelaksanaan) ke pemberi proyek", false, false, RootType.ASSET, "1.8"),
    EMPLOYEE_ADVANCE("Uang Muka Karyawan", "Didebit saat Pengajuan Pembayaran jenis uang muka / perjalanan dinas dibayar", false, false, RootType.ASSET, "1.5.6"),
    COGS("Beban Pokok Penjualan", "HPP - auto-debit saat penjualan barang", false, true, RootType.EXPENSE, "5"),
    OPENING_EQUITY("Ekuitas Saldo Awal", "Contra account untuk import/setup saldo awal", false, true, RootType.EQUITY, "3.1"),
    RETAINED_EARNINGS("Laba Ditahan", "Akun tutup buku akhir tahun fiskal", false, true, RootType.EQUITY, "3.2"),
    CURRENT_PROFIT("Laba Tahun Berjalan", "Laba tahun berjalan (computed di neraca)", false, false, RootType.EQUITY, "3.4"),
    TAX_INPUT("PPN Masukan", "PPN masukan dari pembelian barang/jasa (VAT Input)", false, true, RootType.ASSET, "1.5.3"),
    TAX_OUTPUT("PPN Keluaran", "PPN keluaran dari penjualan barang/jasa (VAT Output)", false, true, RootType.LIABILITY, "2.2.1"),
    INCOME_TAX_EXPENSE("Beban Pajak Penghasilan", "Beban pajak penghasilan badan (PPh Badan)", false, false, RootType.EXPENSE, "6.17"),
    SALES_DISCOUNT("Diskon Penjualan", "Contra revenue: potongan harga penjualan ke pelanggan", false, false, RootType.REVENUE, "4.4"),
    SALES_RETURN("Retur Penjualan", "Contra revenue: pengembalian barang dari pelanggan", false, false, RootType.REVENUE, "4.3"),
    SHIPPING_EXPENSE("Beban Pengiriman", "Biaya ekspedisi/pengiriman barang ke pelanggan", false, false, RootType.EXPENSE, "6.14"),
    FIXED_ASSET("Aset Tetap", "Akun default untuk pencatatan pembelian aset tetap", true, false, RootType.ASSET, "1.6.1", "1.6.2", "1.6.3", "1.6.4", "1.6.5"),
    ACCUM_DEPRECIATION("Akumulasi Penyusutan", "Akun kontra aset untuk akumulasi penyusutan aset tetap", true, false, RootType.ASSET, "1.7.1", "1.7.2", "1.7.3", "1.7.4"),
    DEPRECIATION_EXPENSE("Beban Penyusutan", "Beban penyusutan aset tetap periodik", true, false, RootType.EXPENSE, "6.21", "6.22", "6.23", "6.24"),
    BANK_CHARGE("Beban Administrasi Bank", "Biaya administrasi bank, buku cek, giro", false, false, RootType.EXPENSE, "8.2"),
    INTEREST_EXPENSE("Beban Bunga", "Beban bunga pinjaman bank/pihak ketiga", false, false, RootType.EXPENSE, "8.1"),
    INTEREST_INCOME("Pendapatan Bunga", "Pendapatan bunga deposito/tabungan bank", false, false, RootType.REVENUE, "7.1"),
    FX_GAIN_LOSS("Laba/Rugi Selisih Kurs", "Keuntungan atau kerugian dari selisih nilai tukar mata uang", false, false, RootType.EXPENSE, "8.4"),
    FX_UNREALIZED("Laba/Rugi Kurs Belum Terealisasi", "Selisih kurs belum terealisasi dari revaluasi saldo akhir periode", false, false, RootType.EXPENSE, "8.5"),
    BAD_DEBT_EXPENSE("Beban Piutang Tak Tertagih", "Beban penghapusan piutang yang tidak dapat ditagih", false, false, RootType.EXPENSE, "6.27"),
    ALLOWANCE_DOUBTFUL("Cadangan Kerugian Piutang", "Contra asset: penyisihan piutang ragu-ragu", false, false, RootType.ASSET, "1.2.5"),
    PREPAID_EXPENSE("Biaya Dibayar Dimuka", "Aset lancar: biaya yang sudah dibayar tapi belum jatuh tempo", true, false, RootType.ASSET, "1.5.1", "1.5.2"),
    ACCRUED_EXPENSE("Hutang Beban Akrual", "Kewajiban: beban yang sudah terjadi tapi belum dibayar", false, false, RootType.LIABILITY, "2.2.6"),
    OWNER_DRAWING("Prive / Penarikan Pemilik", "Penarikan modal oleh pemilik/pemegang saham", false, false, RootType.EQUITY, "3.5"),
    OTHER_INCOME("Pendapatan Lain-lain", "Pendapatan di luar usaha utama (non-operating income)", false, false, RootType.REVENUE, "7.4"),
    OTHER_EXPENSE("Beban Lain-lain", "Beban di luar operasional utama (non-operating expense)", false, false, RootType.EXPENSE, "8.7"),
    PRODUCTION_CONVERSION("Biaya Konversi Produksi", "Selisih nilai output vs input produksi (biaya konversi: listrik, tenaga kerja, overhead)", false, false, RootType.EXPENSE, "6.39"),
    INVENTORY_VARIANCE("Selisih Persediaan", "Beban selisih stok opname (inventory variance expense)", false, false, RootType.EXPENSE, "6.38"),
    ROUNDING_ACCOUNT("Pembulatan / Selisih", "Akun untuk pembulatan dan selisih kecil yang tidak material", false, false, RootType.EXPENSE, "8.8"),
    ACCRUED_PAYABLE("Hutang Belum Tagih", "Accrual: posted CR saat STT/BPB submit (barang sudah diterima, invoice belum). Reversed DR saat PI submit.", false, false, RootType.LIABILITY, "2.1.5"),
    PURCHASE_PRICE_VARIANCE("Selisih Harga Pembelian", "Variance antara harga STT (locked) dan harga PI (aktual). DR kalau PI lebih mahal, CR kalau lebih murah.", false, false, RootType.EXPENSE, "5.11");

    private final String label;
    private final String description;
    private final boolean multiAccount;
    private final boolean required;
    private final RootType expectedRootType;
    private final List<String> defaultAccountNumbers;

    SystemAccountRole(String label, String description, boolean multiAccount, boolean required,
                      RootType expectedRootType, String... defaults) {
        this.label = label;
        this.description = description;
        this.multiAccount = multiAccount;
        this.required = required;
        this.expectedRootType = expectedRootType;
        this.defaultAccountNumbers = List.of(defaults);
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean multiAccount() {
        return multiAccount;
    }

    public boolean required() {
        return required;
    }

    public RootType expectedRootType() {
        return expectedRootType;
    }

    public List<String> defaultAccountNumbers() {
        return defaultAccountNumbers;
    }

    public static List<SystemAccountRole> requiredRoles() {
        return Arrays.stream(values()).filter(SystemAccountRole::required).toList();
    }

    public static boolean isRole(String key) {
        return Arrays.stream(values()).anyMatch(r -> r.name().equals(key));
    }

    /**
     * Nomor akun yang dipetakan sebagai akun sistem dibekukan (port {@code systemAccountRenumberError}).
     * Null bila perubahan diizinkan.
     */
    public static String renumberError(String currentNumber, String requestedNumber, List<String> mappedRoles) {
        if (requestedNumber == null || requestedNumber.isEmpty() || requestedNumber.equals(currentNumber)) {
            return null;
        }
        if (mappedRoles == null || mappedRoles.isEmpty()) {
            return null;
        }
        String roles = String.join(", ", new java.util.LinkedHashSet<>(mappedRoles));
        return "Nomor akun " + currentNumber + " tidak dapat diubah karena dipakai sebagai akun sistem (" + roles
                + "). Ubah dulu pemetaan di Pengaturan → Akun Sistem, atau ubah nama akun saja.";
    }
}
