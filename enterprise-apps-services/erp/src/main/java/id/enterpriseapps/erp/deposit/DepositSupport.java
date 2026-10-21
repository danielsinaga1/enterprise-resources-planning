package id.enterpriseapps.erp.deposit;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpQuery;
import id.enterpriseapps.erp.core.Money;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bantuan bersama alur tulis uang muka pelanggan & supplier. */
final class DepositSupport {

    private DepositSupport() {
    }

    /** Ambang sisa/outstanding (Rp 0,01) yang dipakai sumber untuk lunas / sisa. */
    static final BigDecimal CENT = Money.CENT;

    /**
     * Penolakan batal uang muka yang sudah direfund kas (perbaikan atas sumber ENTARIS): jurnal refund berdiri
     * sendiri, jadi membalik jurnal penerimaan/pembayaran secara penuh membuat akun uang muka dan saldo mitra
     * minus. Pembalikan parsial sengaja tidak didukung.
     */
    static final String REFUNDED_CANNOT_CANCEL = "Deposit sudah direfund sebagian/seluruhnya; tidak dapat dibatalkan.";

    /** 400 bila uang muka pernah direfund kas (sebagian atau seluruhnya). */
    static void assertNotRefunded(BigDecimal refundedAmount) {
        if (Money.nz(refundedAmount).signum() > 0) {
            throw ErpException.business(REFUNDED_CANNOT_CANCEL);
        }
    }

    /** Kunci baris (SELECT … FOR UPDATE) — {@code table} selalu konstanta, bukan input pengguna. */
    static void lockRow(EntityManager em, String table, String id) {
        em.createNativeQuery("SELECT id FROM " + table + " WHERE id = :id FOR UPDATE")
                .setParameter("id", id)
                .getResultList();
    }

    /** Kunci faktur terbuka (Submitted/PartiallyPaid/Overdue) milik mitra sebelum offset FIFO. */
    static void lockOpenInvoices(EntityManager em, String table, String partyId) {
        em.createNativeQuery("SELECT id FROM " + table
                        + " WHERE party_id = :partyId AND status IN ('Submitted','PartiallyPaid','Overdue') FOR UPDATE")
                .setParameter("partyId", partyId)
                .getResultList();
    }

    /** {@code refunded_amount += v} atomik — padanan {@code refundedAmount: { increment }}. */
    static void incrementRefunded(EntityManager em, String paymentId, BigDecimal v) {
        em.createNativeQuery("UPDATE payments SET refunded_amount = refunded_amount + :v, updated_at = now() WHERE id = :id")
                .setParameter("v", v)
                .setParameter("id", paymentId)
                .executeUpdate();
    }

    /**
     * Soft-cancel baris ledger impor lama yang deskripsinya memuat nomor pembayaran
     * ({@code description: { contains }} — case-sensitive, wildcard di-escape).
     */
    static void cancelLegacyLedgerRows(EntityManager em, String paymentNumber) {
        em.createQuery("update AccountingLedgerEntry e set e.isCancelled = true "
                        + "where e.description like :p and e.isCancelled = false")
                .setParameter("p", "%" + ErpQuery.escapeLike(paymentNumber) + "%")
                .executeUpdate();
    }

    /** {@code Decimal.toFixed(2)} (HALF_UP, tanpa notasi eksponen) untuk pesan error. */
    static String fixed2(BigDecimal v) {
        return Money.nz(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Kolom skalar aplikasi uang muka (respons Prisma {@code create} tanpa include). */
    static Map<String, Object> applicationJson(String id, String depositPaymentId, String invoiceKey, String invoiceId,
                                               BigDecimal appliedAmount, String journalEntryId, Boolean isCancelled,
                                               Object appliedAt, Object cancelledAt, String createdBy) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("depositPaymentId", depositPaymentId);
        m.put(invoiceKey, invoiceId);
        m.put("appliedAmount", appliedAmount);
        m.put("journalEntryId", journalEntryId);
        m.put("isCancelled", isCancelled);
        m.put("appliedAt", appliedAt);
        m.put("cancelledAt", cancelledAt);
        m.put("createdBy", createdBy);
        return m;
    }
}
