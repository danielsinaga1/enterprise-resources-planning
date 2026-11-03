package id.neravoxa.erp.core.web;

import id.neravoxa.erp.core.security.ErpPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu sidebar platform untuk modul ERP — padanan registry rute {@code client/src/app/routes.ts}.
 *
 * <p>Dipanggil {@code RolePermissionService.getUserMenu()} dengan kode role pengguna. Item hanya
 * tampil bila role punya izin "view" modulnya (sama dengan PermRoute di klien ERP). Seksi ERP
 * tidak muncul sama sekali untuk pengguna tanpa role ERP.
 */
@Component
@RequiredArgsConstructor
public class ErpMenuProvider {

    public static final String BASE = "/dashboard/erp";

    private final ErpPermissionService permissions;

    private record Item(String label, String path, String module) {
    }

    private record Group(String label, String icon, List<Item> items) {
    }

    private static Item i(String label, String path, String module) {
        return new Item(label, BASE + path, module);
    }

    private static final List<Group> GROUPS = List.of(
            new Group("Penjualan", "pi pi-shopping-cart", List.of(
                    i("Pertanyaan Customer", "/inquiries", "quotation"),
                    i("Penawaran", "/quotations", "quotation"),
                    i("Sales Order", "/sales-orders", "sales_order"),
                    i("Surat Jalan", "/delivery-orders", "delivery_order"),
                    i("Invoice Penjualan", "/sales", "sales_invoice"),
                    i("Retur Penjualan", "/sales-returns", "sales_return"),
                    i("Uang Muka Customer", "/customer-deposits", "customer_deposit"))),
            new Group("Pembelian", "pi pi-shopping-bag", List.of(
                    i("Purchase Order", "/purchase-orders", "purchase_order"),
                    i("Penerimaan Barang", "/goods-receipts", "goods_receipt"),
                    i("Invoice Pembelian", "/purchase", "purchase_invoice"),
                    i("Uang Muka Supplier", "/vendor-deposits", "vendor_deposit"))),
            new Group("Kas & Bank", "pi pi-credit-card", List.of(
                    i("Transaksi Kas & Bank", "/payments", "payment"),
                    i("Draft Buku Kas", "/cash-book-drafts", "journal"),
                    i("Rekonsiliasi Bank", "/reconciliation", "reconciliation"),
                    i("Transaksi Berulang", "/recurring", "recurring"),
                    i("Uang Jaminan Proyek", "/project-guarantees", "project_guarantee"))),
            new Group("Persediaan & Produksi", "pi pi-box", List.of(
                    i("Stok & Produksi", "/inventory", "inventory"),
                    i("Stok per Gudang", "/warehouse-stock", "inventory"),
                    i("Stok Opname", "/stock-opname", "stock_opname"),
                    i("Transfer Stok", "/stock-transfers", "stock_transfer"),
                    i("Daftar Gudang", "/warehouses", "warehouse"))),
            new Group("Akuntansi", "pi pi-book", List.of(
                    i("Bagan Akun", "/coa", "coa"),
                    i("Jurnal Umum", "/gl", "journal"),
                    i("Aset Tetap", "/fixed-assets", "fixed_assets"),
                    i("Persetujuan", "/approvals", "approvals"))),
            new Group("Aset & Pemeliharaan", "pi pi-car", List.of(
                    i("Ringkasan", "/ga", "ga_assets"),
                    i("Daftar Aset", "/ga/assets", "ga_assets"),
                    i("Kendaraan", "/ga/vehicle", "ga_vehicle"),
                    i("Laporan Armada", "/ga/vehicle/report", "ga_vehicle"),
                    i("Gedung & Sewa", "/ga/building", "ga_building"),
                    i("Peralatan & Kalibrasi", "/ga/equipment", "ga_equipment"),
                    i("Pemeliharaan", "/ga/maintenance", "ga_maintenance"),
                    i("Laporan Pemeliharaan", "/ga/maintenance/report", "ga_maintenance"),
                    i("Permintaan Barang", "/ga/material-requests", "ga_material_request"),
                    i("Inventaris Kantor", "/ga/inventory", "ga_inventory"),
                    i("Dokumen & Sertifikat", "/ga/documents", "ga_documents"),
                    i("Data Induk Aset", "/ga/master", "ga_master"))),
            new Group("Laporan Keuangan", "pi pi-chart-bar", List.of(
                    i("Semua Laporan", "/reports", "reports"),
                    i("Laba Rugi", "/reports/profit-loss", "reports"),
                    i("Neraca", "/reports/balance-sheet", "reports"),
                    i("Arus Kas", "/reports/cash-flow", "reports"),
                    i("Neraca Saldo", "/reports/trial-balance", "reports"),
                    i("Buku Besar", "/reports/ledger-book", "reports"))),
            new Group("Data Induk ERP", "pi pi-users", List.of(
                    i("Customer & Supplier", "/parties", "parties"))),
            new Group("Sistem ERP", "pi pi-cog", List.of(
                    i("Pengaturan ERP", "/settings", "settings"),
                    i("Pengguna & Hak Akses ERP", "/users", "users"),
                    i("Jejak Audit ERP", "/audit", "audit"),
                    i("Pemeriksaan Sistem", "/health-check", "audit"))));

    /** Seksi menu ERP (format VeronaSidebar: {label, items:[{label, icon, to | items}]}); kosong bila tanpa akses. */
    public List<Map<String, Object>> menuFor(Collection<String> roleCodes) {
        if (!permissions.hasAnyErpRole(roleCodes)) {
            return List.of();
        }
        List<Object> items = new ArrayList<>();
        items.add(link("Dashboard ERP", "pi pi-chart-line", BASE));
        items.add(link("Pengajuan Pembayaran", "pi pi-wallet", BASE + "/payment-requests"));
        for (Group g : GROUPS) {
            List<Map<String, Object>> subs = new ArrayList<>();
            for (Item it : g.items()) {
                if (it.module() == null || permissions.can(roleCodes, it.module(), ErpPermissionService.VIEW)) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("label", it.label());
                    s.put("to", it.path());
                    subs.add(s);
                }
            }
            if (!subs.isEmpty()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("label", g.label());
                m.put("icon", g.icon());
                m.put("items", subs);
                items.add(m);
            }
        }
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("label", "ERP");
        section.put("items", items);
        return List.of(section);
    }

    private static Map<String, Object> link(String label, String icon, String to) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("icon", icon);
        m.put("to", to);
        return m;
    }
}
