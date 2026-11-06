package id.neravoxa.erp.core.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Taksonomi RBAC ERP — sumber kebenaran tunggal modul, capability, role bawaan, dan matriks
 * izin default (port {@code constants/modules.ts} ENTARIS).
 *
 * <p>Perbedaan dengan sumber: kunci role = KODE ROLE PLATFORM (auth_roles.code), karena
 * identitas & role pengguna dikelola auth platform. Pengguna dengan beberapa role platform
 * mendapat gabungan (union) izin dari semua role yang terdaftar di tabel {@code roles} db_erp.
 */
public final class ErpModules {

    private ErpModules() {
    }

    public record ModuleDef(String key, String label, String group) {
    }

    public record CapabilityDef(String key, String label) {
    }

    public record BuiltinRole(String key, String name, String description, boolean superAdmin) {
    }

    public static final List<ModuleDef> MODULES = List.of(
            new ModuleDef("sales_order", "Pesanan Penjualan (SO)", "Penjualan"),
            new ModuleDef("delivery_order", "Surat Jalan (DO)", "Penjualan"),
            new ModuleDef("sales_invoice", "Invoice Penjualan", "Penjualan"),
            new ModuleDef("quotation", "Penawaran & Inquiry", "Penjualan"),
            new ModuleDef("sales_return", "Retur Penjualan", "Penjualan"),
            new ModuleDef("customer_deposit", "Uang Muka Pelanggan", "Penjualan"),
            new ModuleDef("purchase_order", "Pesanan Pembelian (PO)", "Pembelian"),
            new ModuleDef("goods_receipt", "Penerimaan Barang (BPB/STT)", "Pembelian"),
            new ModuleDef("purchase_invoice", "Invoice Pembelian", "Pembelian"),
            new ModuleDef("vendor_deposit", "Uang Muka Vendor", "Pembelian"),
            new ModuleDef("inventory", "Stok & Gudang", "Stok & Produksi"),
            new ModuleDef("stock_transfer", "Transfer Stok", "Stok & Produksi"),
            new ModuleDef("stock_opname", "Stok Opname", "Stok & Produksi"),
            new ModuleDef("production", "Produksi", "Stok & Produksi"),
            new ModuleDef("toll_service", "Jasa Maklon & Stok Titipan", "Stok & Produksi"),
            new ModuleDef("warehouse", "Daftar Gudang", "Stok & Produksi"),
            new ModuleDef("payment", "Transaksi Bank/Kas", "Bank & Kas"),
            new ModuleDef("reconciliation", "Rekonsiliasi Bank", "Bank & Kas"),
            new ModuleDef("recurring", "Transaksi Berulang", "Bank & Kas"),
            new ModuleDef("project_guarantee", "Uang Jaminan Proyek", "Bank & Kas"),
            new ModuleDef("payment_request", "Pengajuan Pembayaran (Keuangan)", "Bank & Kas"),
            new ModuleDef("coa", "Bagan Akun (COA)", "Akuntansi"),
            new ModuleDef("journal", "Buku Besar / Jurnal", "Akuntansi"),
            new ModuleDef("fixed_assets", "Aset Tetap", "Akuntansi"),
            new ModuleDef("approvals", "Approval (pengajuan)", "Akuntansi"),
            new ModuleDef("ga_assets", "Aset", "EAM · Manajemen Aset"),
            new ModuleDef("ga_vehicle", "Kendaraan", "EAM · Manajemen Aset"),
            new ModuleDef("ga_building", "Gedung & Sewa", "EAM · Manajemen Aset"),
            new ModuleDef("ga_equipment", "Equipment & Kalibrasi", "EAM · Manajemen Aset"),
            new ModuleDef("ga_maintenance", "Maintenance & Work Order", "EAM · Manajemen Aset"),
            new ModuleDef("ga_material_request", "Permintaan Barang (MR)", "EAM · Manajemen Aset"),
            new ModuleDef("ga_inventory", "Inventaris (Sparepart/ATK)", "EAM · Manajemen Aset"),
            new ModuleDef("ga_documents", "Dokumen Legal & Sertifikat", "EAM · Manajemen Aset"),
            new ModuleDef("ga_master", "Master Data EAM", "EAM · Manajemen Aset"),
            new ModuleDef("reports", "Laporan", "Laporan"),
            new ModuleDef("parties", "Pelanggan & Vendor", "Master Data"),
            new ModuleDef("settings", "Pengaturan", "Sistem"),
            new ModuleDef("users", "Manajemen User", "Sistem"),
            new ModuleDef("audit", "Jejak Audit & Sistem", "Sistem"));

    public static final List<CapabilityDef> CAPABILITIES = List.of(
            new CapabilityDef("doc.cancel", "Batalkan / finalisasi dokumen (cancel, hapus posting, finalize opname, tutup buku)"),
            new CapabilityDef("approval.decide", "Putuskan pengajuan (setujui/tolak/minta revisi) + kelola aturan approval"),
            new CapabilityDef("approval.bypass", "Lewati antrian approval (pengajuan langsung diproses)"),
            new CapabilityDef("approval.view_all", "Lihat semua pengajuan (bukan hanya milik sendiri)"),
            new CapabilityDef("inventory.negative_stock", "Bypass validasi stok minus"),
            new CapabilityDef("rbac.manage", "Kelola Role & Hak Akses"));

    /** Role bawaan. Kunci = kode role platform (auth_roles.code). */
    public static final List<BuiltinRole> BUILTIN_ROLES = List.of(
            new BuiltinRole("SUPER_ADMIN", "Super Admin", "Super admin platform: akses penuh ERP (tidak bisa dibatasi).", true),
            new BuiltinRole("ERP_ADMIN", "Admin ERP", "Akses penuh modul ERP (super-admin ERP, tidak bisa dibatasi).", true),
            new BuiltinRole("ERP_ACCOUNTANT", "Akuntan / Keuangan", "Operasional keuangan, penjualan, pembelian, stok.", false),
            new BuiltinRole("ERP_PRODUCTION", "Staff Produksi", "Operasional pembelian, stok, produksi, surat jalan.", false),
            new BuiltinRole("ERP_VIEWER", "Viewer ERP", "Hanya melihat (read-only).", false));

    /** Matriks default per role bawaan non-super ("edit" mencakup "view"). */
    public static final Map<String, Map<String, String>> DEFAULT_MATRIX = buildMatrix();

    private static Map<String, Map<String, String>> buildMatrix() {
        Map<String, Map<String, String>> m = new LinkedHashMap<>();
        m.put("ERP_ACCOUNTANT", perms(
                "sales_order", "edit", "delivery_order", "edit", "sales_invoice", "edit", "quotation", "edit",
                "sales_return", "edit", "customer_deposit", "edit",
                "purchase_order", "edit", "goods_receipt", "edit", "purchase_invoice", "edit", "vendor_deposit", "edit",
                "inventory", "edit", "stock_transfer", "edit", "stock_opname", "edit", "production", "edit", "toll_service", "edit", "warehouse", "edit",
                "payment", "edit", "reconciliation", "edit", "recurring", "edit", "project_guarantee", "edit", "payment_request", "edit",
                "coa", "view", "journal", "edit", "fixed_assets", "edit", "approvals", "edit",
                "reports", "view", "parties", "edit"));
        m.put("ERP_PRODUCTION", perms(
                "sales_order", "edit", "delivery_order", "edit", "sales_return", "edit",
                "purchase_order", "edit", "goods_receipt", "edit", "purchase_invoice", "edit", "vendor_deposit", "view",
                "inventory", "edit", "stock_transfer", "edit", "production", "edit", "toll_service", "edit", "warehouse", "view",
                "approvals", "edit", "parties", "edit"));
        m.put("ERP_VIEWER", perms(
                "sales_order", "view", "delivery_order", "view", "sales_invoice", "view", "quotation", "view",
                "purchase_order", "view", "goods_receipt", "view",
                "inventory", "view", "toll_service", "view", "payment", "view", "project_guarantee", "view",
                "coa", "view", "journal", "view", "fixed_assets", "view", "reports", "view"));
        return m;
    }

    private static Map<String, String> perms(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    public static boolean isModule(String key) {
        return MODULES.stream().anyMatch(m -> m.key().equals(key));
    }

    public static boolean isCapability(String key) {
        return CAPABILITIES.stream().anyMatch(c -> c.key().equals(key));
    }
}
