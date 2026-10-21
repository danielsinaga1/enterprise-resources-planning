package id.enterpriseapps.erp.paymentrequest;

import java.util.Set;

/**
 * Konteks pengguna untuk Pengajuan Pembayaran — padanan {@code ViewerCtx} (paymentRequestRules.ts).
 *
 * @param id          id user ERP
 * @param role        role utama (dikirim di {@code GET /meta})
 * @param roleKeys    semua kode role platform; tahap Direktur cocok bila salah satunya = {@code directorRole}
 * @param isSuperAdmin role super-admin ERP
 * @param isFinance   izin {@code payment_request} edit (verifikasi / setujui sebagai Keuangan / bayar)
 * @param canViewAll  izin {@code payment_request} view (tab "Semua")
 */
record PrViewer(String id, String role, Set<String> roleKeys, boolean isSuperAdmin, boolean isFinance,
                boolean canViewAll) {

    /** ENTARIS membandingkan satu role ({@code actor.role === directorRole}); di platform pengguna bisa multi-role. */
    boolean hasRole(String roleKey) {
        return roleKey != null && roleKeys != null && roleKeys.contains(roleKey);
    }
}
