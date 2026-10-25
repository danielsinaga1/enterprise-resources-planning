package id.neravoxa.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.entity.RecurringTemplate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transaksi berulang — port {@code routes/recurring.ts} + {@code services/recurringRunner.ts}:
 * CRUD template, eksekusi manual satu template, jalankan semua yang jatuh tempo (HTTP), dan
 * runner harian terjadwal. Hanya tipe {@code journal} yang dapat dieksekusi otomatis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpRecurringService {

    private static final String NOT_FOUND_REFERENCE = "Data terkait tidak ditemukan. Periksa referensi yang dipilih.";

    private final ErpRecurringExecutor executor;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** Hasil runner terjadwal (untuk log). */
    public record RunOutcome(int executed, int failed, int skipped) {
    }

    // ───────────────────────────── CRUD ─────────────────────────────

    /** GET /recurring — {@code { data: [...] }}, terbaru dulu, + {@code user: { fullName }}. */
    @ErpTx(readOnly = true)
    public ObjectNode list() {
        List<RecurringTemplate> templates = em.createQuery(
                "select t from RecurringTemplate t order by t.createdAt desc", RecurringTemplate.class).getResultList();
        Map<String, String> names = new HashMap<>();
        List<String> userIds = templates.stream().map(RecurringTemplate::getCreatedBy).distinct().toList();
        if (!userIds.isEmpty()) {
            for (Object[] r : em.createQuery("select u.id, u.fullName from User u where u.id in :ids", Object[].class)
                    .setParameter("ids", userIds).getResultList()) {
                names.put((String) r[0], (String) r[1]);
            }
        }
        ArrayNode data = ErpJson.arrayNode();
        for (RecurringTemplate t : templates) {
            ObjectNode n = AccountingJson.pick(t, AccountingJson.RECURRING_TEMPLATE);
            n.set("user", ErpJournalService.userJson(names, t.getCreatedBy()));
            data.add(n);
        }
        ObjectNode out = ErpJson.object();
        out.set("data", data);
        return out;
    }

    /** POST /recurring. */
    @ErpTx
    public ObjectNode create(AccountingRequests.RecurringCreate body, String userId) {
        Integer dayOfMonth = dayOfMonth(body.dayOfMonth(), "Gagal membuat template berulang.");
        Instant nextRunDate = ErpDates.toInstant(body.nextRunDate());
        JsonNode templateData = RecurringTemplateData.parse(body.templateData());

        RecurringTemplate t = new RecurringTemplate();
        t.setName(body.name());
        t.setTemplateType(body.templateType());
        t.setFrequency(body.frequency());
        t.setDayOfMonth(dayOfMonth);
        t.setNextRunDate(nextRunDate);
        t.setTemplateData(templateData);
        t.setCreatedBy(userId);
        em.persist(t);
        AccountingDb.flush(em);
        em.refresh(t);
        return AccountingJson.pick(t, AccountingJson.RECURRING_TEMPLATE);
    }

    /** PUT /recurring/:id — hanya field yang dikirim. */
    @ErpTx
    public ObjectNode update(String id, AccountingRequests.RecurringUpdate body) {
        Integer dayOfMonth = body.dayOfMonth() == null ? null
                : dayOfMonth(body.dayOfMonth(), "Gagal mengupdate template berulang.");
        Instant nextRunDate = null;
        if (body.nextRunDate() != null) {
            nextRunDate = ErpDates.tryInstant(body.nextRunDate());
            if (nextRunDate == null) {
                throw ErpException.business("Tanggal tidak valid.");
            }
        }
        JsonNode templateData = body.templateData() == null ? null : RecurringTemplateData.parse(body.templateData());

        RecurringTemplate t = em.find(RecurringTemplate.class, id);
        if (t == null) {
            throw ErpException.business(NOT_FOUND_REFERENCE);
        }
        if (body.name() != null) {
            t.setName(body.name());
        }
        if (body.templateType() != null) {
            t.setTemplateType(body.templateType());
        }
        if (body.frequency() != null) {
            t.setFrequency(body.frequency());
        }
        if (body.dayOfMonth() != null) {
            t.setDayOfMonth(dayOfMonth);
        }
        if (nextRunDate != null) {
            t.setNextRunDate(nextRunDate);
        }
        if (templateData != null) {
            t.setTemplateData(templateData);
        }
        if (body.isActive() != null) {
            t.setIsActive(body.isActive());
        }
        t.setUpdatedAt(Instant.now());
        AccountingDb.flush(em);
        em.refresh(t);
        return AccountingJson.pick(t, AccountingJson.RECURRING_TEMPLATE);
    }

    /** DELETE /recurring/:id — soft delete (isActive=false). */
    @ErpTx
    public ObjectNode deactivate(String id) {
        RecurringTemplate t = em.find(RecurringTemplate.class, id);
        if (t == null) {
            throw ErpException.business(NOT_FOUND_REFERENCE);
        }
        t.setIsActive(false);
        t.setUpdatedAt(Instant.now());
        return AccountingJson.message("Template berulang dinonaktifkan.");
    }

    /**
     * dayOfMonth: z.coerce.number().min(1).max(31).nullable(). Kolom INTEGER: nilai pecahan ditolak
     * Prisma (500 dengan pesan fallback rute) — perilaku yang sama dipertahankan.
     */
    private static Integer dayOfMonth(JsonNode raw, String failMessage) {
        if (raw == null || raw.isNull()) {
            return null;
        }
        BigDecimal v = ZodLite.requireNumber(raw);
        if (v.compareTo(BigDecimal.ONE) < 0) {
            throw ErpException.business(ZodLite.tooSmall(true, "1"));
        }
        if (v.compareTo(BigDecimal.valueOf(31)) > 0) {
            throw ErpException.business(ZodLite.tooBig(true, "31"));
        }
        if (v.stripTrailingZeros().scale() > 0) {
            throw ErpException.of(500, failMessage);
        }
        return v.intValueExact();
    }

    // ───────────────────────────── eksekusi ─────────────────────────────

    /** POST /recurring/:id/execute — {@code { success, result? , error? }}. */
    public ObjectNode execute(String id, String userId) {
        ErpRecurringExecutor.Snapshot t = executor.load(id);
        if (t == null) {
            throw ErpException.of(404, "Template tidak ditemukan.");
        }
        return runRouteTemplate(t, userId, null);
    }

    /** POST /recurring/run-due — jalankan semua template jatuh tempo (tipe apa pun dicoba). */
    public ObjectNode runDue(String userId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        List<ErpRecurringExecutor.Snapshot> due = executor.due(now);
        ArrayNode results = ErpJson.arrayNode();
        int success = 0;
        for (ErpRecurringExecutor.Snapshot t : due) {
            ObjectNode r = runRouteTemplate(t, userId, now);
            ObjectNode row = results.addObject();
            row.put("templateId", t.id());
            row.put("name", t.name());
            row.put("success", r.get("success").asBoolean());
            if (r.hasNonNull("error")) {
                row.set("error", r.get("error"));
            }
            if (r.get("success").asBoolean()) {
                success++;
            }
        }
        ObjectNode out = AccountingJson.message(success + "/" + due.size() + " template dijalankan.");
        out.set("results", results);
        return out;
    }

    /** {@code executeTemplate} rute: kegagalan dikembalikan sebagai {@code { success: false, error }}. */
    private ObjectNode runRouteTemplate(ErpRecurringExecutor.Snapshot t, String userId, Instant runAt) {
        ObjectNode out = ErpJson.object();
        if (!"journal".equals(t.templateType())) {
            out.put("success", false);
            out.put("error", "Eksekusi otomatis untuk tipe " + t.templateType()
                    + " belum didukung. Gunakan tipe journal.");
            return out;
        }
        try {
            ObjectNode result = executor.executeJournal(t.id(), userId,
                    runAt != null ? runAt : Instant.now().truncatedTo(ChronoUnit.MILLIS), false);
            out.put("success", true);
            out.set("result", result);
        } catch (RuntimeException e) {
            log.error("Eksekusi template berulang {} gagal", t.id(), e);
            String msg = AccountingDb.rootMessage(e);
            out.put("success", false);
            out.put("error", msg == null || msg.isBlank() ? "Gagal mengeksekusi template." : msg);
        }
        return out;
    }

    /**
     * Runner harian (dipanggil scheduler di dalam kunci job): eksekusi semua template journal yang
     * jatuh tempo atas nama Admin aktif pertama; tipe lain dilewati. Satu kegagalan tidak
     * menghentikan batch.
     */
    public RunOutcome runDueScheduled() {
        String userId = executor.schedulerUserId();
        if (userId == null) {
            log.warn("Recurring runner: tidak ada Admin aktif untuk atribusi entri terjadwal — dilewati");
            return new RunOutcome(0, 0, 0);
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        int executed = 0;
        int failed = 0;
        int skipped = 0;
        for (ErpRecurringExecutor.Snapshot t : executor.due(now)) {
            if (!"journal".equals(t.templateType())) {
                // Hanya tipe journal yang didukung untuk auto-run.
                skipped++;
                continue;
            }
            try {
                executor.executeJournal(t.id(), userId, now, true);
                executed++;
            } catch (RuntimeException e) {
                log.error("Eksekusi template berulang terjadwal {} gagal", t.id(), e);
                failed++;
            }
        }
        return new RunOutcome(executed, failed, skipped);
    }
}
