package id.neravoxa.erp.cashbook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Posting Draft Buku Kas — port {@code postCashBookDraft} ({@code services/cashBookDraftPost.ts}):
 * SATU jurnal seimbang per baris non-skip (DR/CR dari {@link CashBookRules}), semuanya dalam satu
 * transaksi per draft. Draft diklaim atomik sehingga posting ganda mustahil. Koreksi manusia
 * dipelajari (pola → akun) setelah jurnal ditulis.
 *
 * <p>Default all-or-nothing: baris tidak valid membatalkan posting (transaksi di-rollback), lalu error
 * per baris dicatat di transaksi terpisah dan dikembalikan 400 {@code { error, rowErrors }}.
 * {@code partial=true} memposting baris yang valid dan mencatat error sisanya (PartiallyPosted).
 * Sengaja tanpa {@code @ErpTx}: rollback posting harus selesai sebelum error dicatat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpCashBookPostService {

    private final ErpCashBookPostWriter writer;

    public Map<String, Object> post(String id, String userId, boolean partial) {
        try {
            return writer.postInTx(id, userId, partial);
        } catch (ErpCashBookPostWriter.PostBlockedException blocked) {
            try {
                writer.recordPostErrors(id, blocked.failed);
            } catch (RuntimeException e) {
                log.warn("Gagal mencatat error posting draft buku kas {}: {}", id, e.getMessage());
            }
            throw ErpCashBookDraftService.rowErrors(blocked.failed.stream().map(ErpCashBookPostWriter.PostFailure::fmt).toList());
        }
    }
}
