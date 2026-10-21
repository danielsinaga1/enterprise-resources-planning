package id.enterpriseapps.erp.deposit;

import id.enterpriseapps.erp.entity.PaymentType;

/**
 * Jenis uang muka. Daftar/ringkasan/saldo uang muka pelanggan dan supplier identik kecuali
 * tipe pembayaran, entity aplikasinya, nama relasi di JSON, dan nama kolom sort mitra —
 * parameter itu dikumpulkan di sini agar {@link ErpDepositQueryService} dipakai bersama.
 */
enum DepositKind {

    /** Uang muka pelanggan (2.1.2) — {@code routes/customerDeposits.ts}. */
    CUSTOMER(PaymentType.CustomerDeposit, "CustomerDepositApplication", "customerDepositApplications",
            "salesInvoiceId", "customer", "Gagal mengambil data uang muka customer."),

    /** Uang muka supplier (1.3) — {@code routes/vendorDeposits.ts}. */
    VENDOR(PaymentType.VendorDeposit, "VendorDepositApplication", "depositApplications",
            "purchaseInvoiceId", "supplier", "Gagal mengambil data uang muka supplier.");

    /** Nilai {@code payments.payment_type}. */
    final PaymentType paymentType;
    /** Nama entity JPQL aplikasi uang muka. */
    final String applicationEntity;
    /** Nama relasi aplikasi pada Payment (kunci JSON yang dibaca UI). */
    final String applicationsKey;
    /** FK faktur pada entity aplikasi. */
    final String invoiceIdField;
    /** Nama field sort untuk nama mitra ({@code customer} / {@code supplier}). */
    final String partySortField;
    /** Pesan 500 daftar mode lama (status DocumentStatus tidak dikenal → Prisma melempar). */
    final String listErrorMessage;

    DepositKind(PaymentType paymentType, String applicationEntity, String applicationsKey, String invoiceIdField,
                String partySortField, String listErrorMessage) {
        this.paymentType = paymentType;
        this.applicationEntity = applicationEntity;
        this.applicationsKey = applicationsKey;
        this.invoiceIdField = invoiceIdField;
        this.partySortField = partySortField;
        this.listErrorMessage = listErrorMessage;
    }
}
