package id.enterpriseapps.erp.fulfillment;

import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.Quotation;
import id.enterpriseapps.erp.entity.QuotationStatus;
import id.enterpriseapps.erp.entity.SalesOrder;
import id.enterpriseapps.erp.entity.SalesOrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Validasi tautan antar dokumen penjualan — port {@code services/salesDocumentLinks.ts}. */
@Service
public class ErpSalesDocumentLinkService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Penawaran yang boleh ditautkan ke SO: customer sama dan berstatus Accepted. */
    @ErpTx(readOnly = true)
    public void assertQuotationLinkable(String quotationId, String partyId) {
        Quotation q = em.find(Quotation.class, quotationId);
        if (q == null) {
            throw ErpException.business("Penawaran tidak ditemukan.");
        }
        if (!Objects.equals(q.getPartyId(), partyId)) {
            throw ErpException.business("Penawaran " + q.getQuotationNumber() + " milik customer lain.");
        }
        if (q.getStatus() != QuotationStatus.Accepted) {
            throw ErpException.business("Penawaran " + q.getQuotationNumber()
                    + " belum diterima customer (status " + q.getStatus() + ").");
        }
    }

    /**
     * quotationId yang disimpan saat edit SO: field tidak dikirim = tautan lama dipertahankan,
     * id berubah divalidasi, null/kosong = tautan dilepas.
     *
     * @param present   field {@code quotationId} ada di body (undefined vs null)
     */
    @ErpTx(readOnly = true)
    public String resolveSalesOrderQuotationId(boolean present, String requested, String current, String partyId) {
        if (!present) {
            return current;
        }
        if (requested == null || requested.isEmpty()) {
            return null;
        }
        if (!requested.equals(current)) {
            assertQuotationLinkable(requested, partyId);
            return requested;
        }
        // Tautan yang sama dipertahankan: cukup customer yang harus tetap cocok.
        Quotation q = em.find(Quotation.class, requested);
        if (q != null && !Objects.equals(q.getPartyId(), partyId)) {
            throw ErpException.business("Penawaran " + q.getQuotationNumber() + " milik customer lain.");
        }
        return requested;
    }

    /**
     * SO yang boleh ditautkan ke Surat Jalan Sementara: customer sama dan (untuk tautan baru) belum dibatalkan.
     * Tautan yang dipertahankan saat edit tidak diblok perubahan status SO sesudahnya.
     */
    @ErpTx(readOnly = true)
    public void assertSalesOrderLinkable(String salesOrderId, String partyId, boolean keptLink) {
        SalesOrder so = em.find(SalesOrder.class, salesOrderId);
        if (so == null) {
            throw ErpException.business("Pesanan penjualan tidak ditemukan.");
        }
        if (!Objects.equals(so.getPartyId(), partyId)) {
            throw ErpException.business("Pesanan " + so.getSoNumber() + " milik customer lain.");
        }
        if (!keptLink && so.getStatus() == SalesOrderStatus.Cancelled) {
            throw ErpException.business("Pesanan " + so.getSoNumber() + " sudah dibatalkan.");
        }
    }
}
