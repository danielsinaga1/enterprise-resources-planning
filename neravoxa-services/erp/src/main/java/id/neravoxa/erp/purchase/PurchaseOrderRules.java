package id.neravoxa.erp.purchase;

import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.entity.DocumentStatus;
import id.neravoxa.erp.entity.PurchaseOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Aturan murni Purchase Order (tanpa akses DB) — port {@code purchaseOrderRules.ts} dan
 * {@code purchaseOrderLifecycle.ts}. PO tetap dokumen planning: tidak ada efek GL/stok.
 *
 * <pre>
 *   Draft ──Ajukan──► (aturan cocok) Draft+Pending ──approve──► Submitted
 *     ▲                     │ reject/revise
 *     └──── Draft+Rejected ◄┘
 *   Draft ──Ajukan──► (tanpa aturan) Submitted
 * </pre>
 * Nomor PO diberikan saat dibuat (draf juga); menghapus draf bisa meninggalkan celah nomor —
 * dapat diterima karena PO tidak punya GL. Method {@code check*} mengembalikan pesan error atau null.
 */
final class PurchaseOrderRules {

    static final String PO_APPROVAL_PENDING = "Pending";
    static final String PO_APPROVAL_REJECTED = "Rejected";

    static final String PO_INCREASE_NEEDS_APPROVAL_ERROR =
            "Kenaikan nilai PO melebihi batas persetujuan. Ajukan ulang (batalkan PO ini dan buat PO baru, atau turunkan nilainya).";

    static final int CLOSE_REASON_MIN = 5;
    static final int CLOSE_REASON_MAX = 500;

    private PurchaseOrderRules() {
    }

    /** Baris PO untuk derivasi status penerimaan. */
    record ReceiptLine(BigDecimal quantity, BigDecimal qtyReceived) {
    }

    // ───────────────────────────── lifecycle ─────────────────────────────

    static boolean isPendingApproval(DocumentStatus status, String approvalStatus) {
        return status == DocumentStatus.Draft && PO_APPROVAL_PENDING.equals(approvalStatus);
    }

    static boolean isPendingApproval(PurchaseOrder po) {
        return isPendingApproval(po.getStatus(), po.getApprovalStatus());
    }

    /** Status tampilan ("Menunggu Persetujuan" = Draf dengan pengajuan Pending). */
    static String displayStatus(PurchaseOrder po) {
        if (isPendingApproval(po)) {
            return "PendingApproval";
        }
        DocumentStatus s = po.getStatus();
        if (s == DocumentStatus.Draft || s == DocumentStatus.Submitted || s == DocumentStatus.Cancelled) {
            return s.name();
        }
        return "Submitted";
    }

    static String checkSubmittable(PurchaseOrder po) {
        if (po.getStatus() != DocumentStatus.Draft) {
            return "Hanya PO berstatus Draf yang bisa diajukan.";
        }
        if (isPendingApproval(po)) {
            return "PO sedang menunggu persetujuan.";
        }
        return null;
    }

    static String checkDeletable(PurchaseOrder po) {
        if (po.getStatus() != DocumentStatus.Draft) {
            return "Hanya PO Draf yang bisa dihapus. PO yang sudah dipesan dibatalkan.";
        }
        if (isPendingApproval(po)) {
            return "PO sedang menunggu persetujuan, tidak bisa dihapus.";
        }
        return null;
    }

    /** Pengajuan Pending memblokir edit (approver harus melihat apa yang diajukan). */
    static String checkEditableLifecycle(PurchaseOrder po) {
        return isPendingApproval(po) ? "PO sedang menunggu persetujuan, tidak bisa diubah." : null;
    }

    /** "Batalkan" hanya untuk PO yang sudah dipesan; draf dihapus. */
    static String checkCancellable(PurchaseOrder po) {
        if (po.getStatus() == DocumentStatus.Cancelled) {
            return "PO sudah dibatalkan.";
        }
        if (po.getStatus() == DocumentStatus.Draft) {
            return "PO Draf tidak dibatalkan. Hapus draf.";
        }
        return null;
    }

    /** Bisa dipakai sebagai sumber STT/PI: sudah dipesan (Submitted) dan belum ditutup. */
    static String checkReceivable(PurchaseOrder po) {
        if (po.getStatus() == DocumentStatus.Cancelled) {
            return "Purchase Order sudah dibatalkan, tidak bisa dipakai untuk STT.";
        }
        if (po.getStatus() != DocumentStatus.Submitted) {
            return "Purchase Order belum dipesan (masih Draf / menunggu persetujuan).";
        }
        if (po.getClosedAt() != null) {
            return "Purchase Order sudah ditutup, tidak bisa dipakai untuk STT.";
        }
        return null;
    }

    record RuleLike(BigDecimal minAmount, Boolean isActive) {
    }

    /** Ada aturan aktif yang berlaku untuk grand total ini (grandTotal ≥ minAmount)? */
    static boolean matchesApprovalRule(List<RuleLike> rules, double grandTotal) {
        for (RuleLike r : rules) {
            if (!Boolean.FALSE.equals(r.isActive()) && JsNumbers.n(r.minAmount()) <= grandTotal) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mengedit PO yang sudah dipesan tidak boleh melewati approval: menaikkan grand total ke nilai
     * yang dicakup aturan aktif ditolak. Penurunan, nilai sama, kenaikan tanpa aturan cocok, dan edit
     * draf tetap boleh. (Dipilih ketimbang mengalihkan ke approval: penolakan ulang akan
     * "membatalkan pesanan" PO yang sudah diterima supplier pada nilai lama.)
     */
    static String checkSubmittedEditTotal(DocumentStatus status, double previousGrandTotal, double newGrandTotal,
                                          List<RuleLike> rules) {
        if (status != DocumentStatus.Submitted) {
            return null;
        }
        if (!(newGrandTotal > previousGrandTotal)) {
            return null;
        }
        if (!matchesApprovalRule(rules, newGrandTotal)) {
            return null;
        }
        return PO_INCREASE_NEEDS_APPROVAL_ERROR;
    }

    /** Hasil "Ajukan": aturan cocok → tetap Draf + Pending; selain itu langsung Submitted. */
    record SubmitOutcome(DocumentStatus status, String approvalStatus) {
    }

    static SubmitOutcome resolveSubmitOutcome(boolean ruleMatched) {
        return ruleMatched
                ? new SubmitOutcome(DocumentStatus.Draft, PO_APPROVAL_PENDING)
                : new SubmitOutcome(DocumentStatus.Submitted, null);
    }

    // ───────────────────────────── penerimaan ─────────────────────────────

    /**
     * Cancelled → "Cancelled", ditutup → "Closed"; selain itu dari item: belum ada yang diterima →
     * Unreceived, semua baris diterima ≥ dipesan → Full, di antaranya → Partial. PO tanpa item = Unreceived.
     */
    static String receiptStatus(DocumentStatus status, Instant closedAt, List<ReceiptLine> items) {
        if (status == DocumentStatus.Cancelled) {
            return "Cancelled";
        }
        if (closedAt != null) {
            return "Closed";
        }
        if (items.isEmpty()) {
            return "Unreceived";
        }
        boolean anyReceived = items.stream().anyMatch(i -> Money.nz(i.qtyReceived()).signum() > 0);
        if (!anyReceived) {
            return "Unreceived";
        }
        boolean all = items.stream().allMatch(i -> Money.nz(i.qtyReceived()).compareTo(Money.nz(i.quantity())) >= 0);
        return all ? "Full" : "Partial";
    }

    /** Sisa qty satu baris, tidak pernah negatif (lebih terima dihitung 0). */
    static double remainingQty(ReceiptLine item) {
        BigDecimal rest = Money.nz(item.quantity()).subtract(Money.nz(item.qtyReceived()));
        return rest.signum() > 0 ? rest.doubleValue() : 0d;
    }

    static String checkEditable(DocumentStatus status, Instant closedAt, List<BigDecimal> itemQtyReceived,
                                long linkedReceiptCount, long linkedInvoiceCount) {
        if (status == DocumentStatus.Cancelled) {
            return "PO yang sudah dibatalkan tidak bisa diubah.";
        }
        if (closedAt != null) {
            return "PO yang sudah ditutup tidak bisa diubah.";
        }
        if (linkedReceiptCount > 0 || itemQtyReceived.stream().anyMatch(q -> Money.nz(q).signum() > 0)) {
            return "PO sudah memiliki STT/penerimaan barang, tidak bisa diubah. Buat PO baru atau tutup PO ini.";
        }
        if (linkedInvoiceCount > 0) {
            return "PO sudah dipakai di Purchase Invoice, tidak bisa diubah.";
        }
        return null;
    }

    static String checkClosable(DocumentStatus status, Instant closedAt, List<ReceiptLine> items, String reason) {
        if (status == DocumentStatus.Cancelled) {
            return "PO sudah dibatalkan.";
        }
        if (closedAt != null) {
            return "PO sudah ditutup.";
        }
        if ("Full".equals(receiptStatus(status, closedAt, items))) {
            return "PO sudah diterima penuh, tidak perlu ditutup.";
        }
        String text = reason == null ? "" : reason.trim();
        if (text.length() < CLOSE_REASON_MIN) {
            return "Alasan penutupan wajib diisi (minimal " + CLOSE_REASON_MIN + " karakter).";
        }
        if (text.length() > CLOSE_REASON_MAX) {
            return "Alasan penutupan maksimal " + CLOSE_REASON_MAX + " karakter.";
        }
        return null;
    }

    // ───────────────────────────── link baris STT ↔ PO ─────────────────────────────

    record PoItemRef(String id, String inventoryItemId) {
    }

    record ReceiptLineRef(String inventoryItemId, String itemName, String purchaseOrderItemId) {
    }

    /**
     * Baris STT yang menunjuk PO tetapi tanpa link baris PO: cocokkan ke baris PO dengan
     * inventoryItemId sama. Tepat satu kandidat → link; tidak ada → biarkan (item ekstra di luar PO);
     * lebih dari satu → lempar (ambigu). Urutan hasil = urutan input.
     */
    static List<String> resolvePoItemLinks(List<ReceiptLineRef> lines, List<PoItemRef> poItems,
                                           Function<ReceiptLineRef, ErpException> onAmbiguous) {
        List<String> out = new ArrayList<>();
        for (ReceiptLineRef line : lines) {
            if (line.purchaseOrderItemId() != null && !line.purchaseOrderItemId().isEmpty()) {
                out.add(line.purchaseOrderItemId());
                continue;
            }
            List<PoItemRef> candidates = poItems.stream()
                    .filter(pi -> pi.inventoryItemId() != null && pi.inventoryItemId().equals(line.inventoryItemId()))
                    .toList();
            if (candidates.size() == 1) {
                out.add(candidates.get(0).id());
            } else if (candidates.size() > 1) {
                throw onAmbiguous.apply(line);
            } else {
                out.add(null);
            }
        }
        return out;
    }
}
