package id.enterpriseapps.erp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.approval.ErpApprovalHandler;
import id.enterpriseapps.erp.entity.ApprovalRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handler approval khusus uji untuk tipe {@value #TYPE}: mencatat panggilan, menulis satu baris "efek"
 * (notifikasi bertipe admin_test_effect) di transaksi keputusan untuk membuktikan rollback, bisa
 * diperlambat ({@code sleepMs}) atau digagalkan ({@code fail}) lewat payload; mendukung tolak/revisi yang
 * mengembalikan dokumen sumber ({@code returnSource}) dan pengajuan ulang.
 */
@Component
public class ErpAdminTestApprovalHandler implements ErpApprovalHandler {

    public static final String TYPE = "AdminTestDoc";
    static final Map<String, AtomicInteger> APPROVED = new ConcurrentHashMap<>();
    static final List<String> DECLINED = new CopyOnWriteArrayList<>();

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    @Override
    public String documentType() {
        return TYPE;
    }

    @Override
    @ErpTx
    public ApprovedDocument approve(ApprovalRequest request, JsonNode body, JsonNode before, String approverUserId, String note) {
        APPROVED.computeIfAbsent(request.getId(), k -> new AtomicInteger()).incrementAndGet();
        long sleep = body == null ? 0 : body.path("sleepMs").asLong(0);
        if (sleep > 0) {
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        em.createNativeQuery("INSERT INTO notifications (id, user_id, type, title, message, is_read, metadata, created_at) "
                        + "VALUES (?1, ?2, 'admin_test_effect', 'efek', 'efek', true, CAST(?3 AS jsonb), now())")
                .setParameter(1, UUID.randomUUID().toString())
                .setParameter(2, request.getRequestedBy())
                .setParameter(3, "{\"requestId\":\"" + request.getId() + "\"}")
                .executeUpdate();
        if (body != null && body.path("fail").asBoolean(false)) {
            throw ErpException.business("Gagal uji eksekusi.");
        }
        return new ApprovedDocument(UUID.randomUUID().toString(), "ATD-" + (body == null ? "" : body.path("label").asText()));
    }

    /** Payload {@code returnSource: true} meniru draf Data Produksi yang dikembalikan ke pengaju. */
    @Override
    public boolean declineReturningSource(ApprovalRequest request, String action, String approverUserId, String note) {
        DECLINED.add(request.getId() + ":" + action + ":" + note);
        return request.getPayload() != null && request.getPayload().path("returnSource").asBoolean(false);
    }

    /** Skema uji: label wajib; nilai antrian = payload.amount. */
    @Override
    public BigDecimal resubmitAmount(ApprovalRequest request, JsonNode payload) {
        if (payload.path("label").asText("").isBlank()) {
            throw ErpException.business("Data uji tidak valid: label wajib diisi.");
        }
        return new BigDecimal(payload.path("amount").asText("0"));
    }
}
