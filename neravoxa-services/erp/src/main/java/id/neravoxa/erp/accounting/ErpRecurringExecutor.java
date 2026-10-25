package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.core.security.ErpPermissionService;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.JournalEntry;
import id.neravoxa.erp.entity.JournalItem;
import id.neravoxa.erp.entity.RecurringTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Eksekusi satu template berulang tipe {@code journal} — port {@code executeTemplate}
 * (routes/recurring.ts) dan {@code executeJournalTemplate} (services/recurringRunner.ts).
 *
 * <p>Setiap template diposting di transaksinya sendiri (REQUIRES_NEW) seperti
 * {@code prisma.$transaction} per template di sumber: kegagalan satu template tidak membatalkan
 * batch maupun transaksi kunci job terjadwal. Pembaruan lastRunDate/nextRunDate ikut di transaksi
 * yang sama dengan jurnalnya sehingga template tidak pernah dijalankan dua kali untuk satu periode.
 * Tidak ada dampak saldo mitra (sama dengan sumber).
 */
@Service
@RequiredArgsConstructor
public class ErpRecurringExecutor {

    /** Potret template untuk orkestrasi di luar transaksi. */
    record Snapshot(String id, String name, String templateType, String frequency, Integer dayOfMonth) {
    }

    private final ErpFiscalYearService fiscalYears;
    private final ErpGlPostingService gl;
    private final ErpPermissionService permissions;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    @ErpTx(readOnly = true)
    public Snapshot load(String id) {
        RecurringTemplate t = em.find(RecurringTemplate.class, id);
        return t == null ? null : snapshot(t);
    }

    /** Template aktif yang nextRunDate-nya sudah lewat. */
    @ErpTx(readOnly = true)
    public List<Snapshot> due(Instant now) {
        return em.createQuery("select t from RecurringTemplate t where t.isActive = true and t.nextRunDate <= :now",
                        RecurringTemplate.class)
                .setParameter("now", now)
                .getResultList().stream().map(ErpRecurringExecutor::snapshot).toList();
    }

    /**
     * Pengguna atas nama eksekusi terjadwal: Admin aktif pertama (padanan role 'Admin' ENTARIS =
     * role super-admin ERP), sama dengan konvensi audit otomatis.
     */
    @ErpTx(readOnly = true)
    public String schedulerUserId() {
        List<String> keys = permissions.superAdminRoleKeys();
        if (keys.isEmpty()) {
            return null;
        }
        List<String> ids = em.createQuery("select u.id from User u where u.isActive = true and u.role in :keys "
                        + "order by u.createdAt asc", String.class)
                .setParameter("keys", keys)
                .setMaxResults(1)
                .getResultList();
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * Posting jurnal dari {@code templateData} lalu set lastRunDate = {@code runAt} dan nextRunDate
     * dihitung dari {@code runAt}. {@code scheduled} memilih varian runner (keterangan baris jatuh ke
     * nama template bila narasi kosong). Mengembalikan jurnal + baris (bentuk {@code include: { items }}).
     */
    @ErpTx(propagation = Propagation.REQUIRES_NEW)
    public ObjectNode executeJournal(String templateId, String userId, Instant runAt, boolean scheduled) {
        RecurringTemplate t = em.find(RecurringTemplate.class, templateId, LockModeType.PESSIMISTIC_WRITE);
        if (t == null) {
            throw ErpException.of(404, "Template tidak ditemukan.");
        }
        JsonNode data = t.getTemplateData();
        String narration = text(data, "narration");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        FiscalYear fy = fiscalYears.getOpenFiscalYear(now);

        ErpGlPostingService.Builder journal = gl.journal()
                .date(now)
                .narration(narration != null ? narration : t.getName())
                .fiscalYear(fy.getId())
                .createdBy(userId)
                .submittedAt(now);
        JsonNode items = data == null ? null : data.get("items");
        if (items != null && !items.isNull() && !items.isArray()) {
            throw ErpException.business("Gagal mengeksekusi template.");
        }
        if (items != null && items.isArray()) {
            for (JsonNode item : items) {
                String description = text(item, "description");
                if (description == null) {
                    description = narration != null ? narration : (scheduled ? t.getName() : null);
                }
                journal.line(text(item, "accountId"), amount(item.get("debit")), amount(item.get("credit")),
                        description, text(item, "partyId"));
            }
        }
        JournalEntry je = journal.post();
        List<JournalItem> posted = new ArrayList<>(je.getItems());

        t.setLastRunDate(runAt);
        t.setNextRunDate(RecurringSchedule.next(t.getFrequency(), runAt, t.getDayOfMonth()));
        em.flush();
        em.refresh(je);

        ObjectNode out = AccountingJson.pick(je, AccountingJson.JOURNAL_ENTRY);
        ArrayNode arr = out.putArray("items");
        posted.forEach(i -> arr.add(AccountingJson.pick(i, AccountingJson.JOURNAL_ITEM)));
        return out;
    }

    /** {@code item.debit || 0}. */
    private static BigDecimal amount(JsonNode v) {
        BigDecimal n = ZodLite.coerce(v);
        return n == null ? BigDecimal.ZERO : Money.nz(n);
    }

    /** Teks non-kosong atau null ({@code x || fallback} di sumber). */
    private static String text(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isEmpty() ? null : s;
    }

    private static Snapshot snapshot(RecurringTemplate t) {
        return new Snapshot(t.getId(), t.getName(), t.getTemplateType(), t.getFrequency(), t.getDayOfMonth());
    }
}
