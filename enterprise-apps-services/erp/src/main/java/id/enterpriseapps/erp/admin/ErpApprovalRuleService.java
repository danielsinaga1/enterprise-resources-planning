package id.enterpriseapps.erp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.Money;
import id.enterpriseapps.erp.entity.ApprovalRequest;
import id.enterpriseapps.erp.entity.ApprovalRule;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aturan approval (ambang nilai per tipe dokumen) + cek "perlu approval?" + pengajuan generik
 * {@code POST /approvals/requests} — port bagian aturan di {@code routes/approvals.ts}.
 *
 * <p>Body diterima mentah karena sumber tidak memvalidasinya: {@code Number(minAmount)} yang NaN atau field
 * wajib yang kosong membuat Prisma gagal dan route menjawab 500 dengan pesan khusus route — perilaku itu
 * dipertahankan (pesan sama, status sama).
 */
@Slf4j
@Service
public class ErpApprovalRuleService {

    static final String PENDING = "Pending";

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    @ErpTx(readOnly = true)
    public List<ApprovalRule> list() {
        return em.createQuery("select r from ApprovalRule r order by r.documentType asc, r.minAmount asc", ApprovalRule.class)
                .getResultList();
    }

    @ErpTx
    public ApprovalRule create(JsonNode body) {
        String failed = "Gagal membuat aturan approval.";
        String documentType = requiredText(body, "documentType", failed);
        String requiredRole = requiredText(body, "requiredRole", failed);
        BigDecimal minAmount = AdminJs.decimal(field(body, "minAmount"));
        if (minAmount == null) {
            throw ErpException.of(500, failed);
        }
        ApprovalRule rule = new ApprovalRule();
        rule.setDocumentType(documentType);
        rule.setMinAmount(Money.round2(minAmount));
        rule.setRequiredRole(requiredRole);
        rule.setIsActive(true);
        em.persist(rule);
        em.flush();
        return rule;
    }

    /** Field yang tidak dikirim tidak diubah (Prisma: undefined), kecuali minAmount yang selalu di-Number(). */
    @ErpTx
    public ApprovalRule update(String id, JsonNode body) {
        String failed = "Gagal memperbarui aturan.";
        ApprovalRule rule = em.find(ApprovalRule.class, id);
        BigDecimal minAmount = AdminJs.decimal(field(body, "minAmount"));
        if (rule == null || minAmount == null) {
            throw ErpException.of(500, failed);
        }
        JsonNode documentType = field(body, "documentType");
        if (!documentType.isMissingNode()) {
            rule.setDocumentType(requiredText(body, "documentType", failed));
        }
        JsonNode requiredRole = field(body, "requiredRole");
        if (!requiredRole.isMissingNode()) {
            rule.setRequiredRole(requiredText(body, "requiredRole", failed));
        }
        JsonNode isActive = field(body, "isActive");
        if (!isActive.isMissingNode()) {
            if (!isActive.isBoolean()) {
                throw ErpException.of(500, failed);
            }
            rule.setIsActive(isActive.booleanValue());
        }
        rule.setMinAmount(Money.round2(minAmount));
        em.flush();
        return rule;
    }

    @ErpTx
    public void delete(String id) {
        ApprovalRule rule = em.find(ApprovalRule.class, id);
        if (rule == null) {
            throw ErpException.of(500, "Gagal menghapus aturan.");
        }
        em.remove(rule);
    }

    /** {@code GET /check}: aturan aktif dengan ambang tertinggi ≤ nilai dokumen. */
    @ErpTx(readOnly = true)
    public Map<String, Object> check(String documentTypeParam, String amountParam) {
        String documentType = documentTypeParam == null ? "" : documentTypeParam.strip();
        String rawAmount = amountParam == null ? "" : amountParam.strip();
        BigDecimal amount = rawAmount.isEmpty() ? null : AdminJs.decimal(rawAmount);
        if (documentType.isEmpty() || amount == null) {
            throw ErpException.business("Parameter documentType dan amount (angka) wajib diisi.");
        }
        ApprovalRule rule = matchRule(documentType, true, amount);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("required", rule != null);
        out.put("rule", rule);
        return out;
    }

    /**
     * {@code POST /requests} — pengajuan generik berbasis aturan (tanpa payload). Field yang tidak dikirim
     * tidak ikut memfilter (semantik undefined Prisma).
     *
     * @return {@code [status, body]}
     */
    @ErpTx
    public Object[] submitRequest(JsonNode body, String userId) {
        String failed = "Gagal membuat request approval.";
        JsonNode typeNode = field(body, "documentType");
        JsonNode idNode = field(body, "documentId");
        JsonNode numberNode = field(body, "documentNumber");
        BigDecimal amount = AdminJs.decimal(field(body, "amount"));
        if (amount == null || invalidText(typeNode, false) || invalidText(idNode, true) || invalidText(numberNode, true)) {
            throw ErpException.of(500, failed);
        }
        String documentType = typeNode.isMissingNode() ? null : typeNode.textValue();
        ApprovalRule rule = matchRule(documentType, !typeNode.isMissingNode(), amount);
        if (rule == null) {
            return new Object[]{200, ErpJson.map("required", false, "message", "Tidak perlu approval.")};
        }

        StringBuilder jpql = new StringBuilder("select r from ApprovalRequest r where r.status = :st");
        if (!typeNode.isMissingNode()) {
            jpql.append(" and r.documentType = :t");
        }
        if (!idNode.isMissingNode()) {
            jpql.append(idNode.isNull() ? " and r.documentId is null" : " and r.documentId = :d");
        }
        TypedQuery<ApprovalRequest> q = em.createQuery(jpql.toString(), ApprovalRequest.class).setParameter("st", PENDING);
        if (!typeNode.isMissingNode()) {
            q.setParameter("t", documentType);
        }
        if (!idNode.isMissingNode() && !idNode.isNull()) {
            q.setParameter("d", idNode.textValue());
        }
        if (!q.setMaxResults(1).getResultList().isEmpty()) {
            throw ErpException.business("Sudah ada request approval yang pending.");
        }
        if (documentType == null) {
            // documentType wajib di tabel: sumber gagal di Prisma create → 500.
            throw ErpException.of(500, failed);
        }

        ApprovalRequest request = new ApprovalRequest();
        request.setDocumentType(documentType);
        request.setDocumentId(idNode.isMissingNode() || idNode.isNull() ? null : idNode.textValue());
        request.setDocumentNumber(numberNode.isMissingNode() || numberNode.isNull() ? null : numberNode.textValue());
        request.setAmount(Money.round2(amount));
        request.setRequestedBy(userId);
        request.setStatus(PENDING);
        em.persist(request);
        em.flush();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("required", true);
        out.put("request", AdminShape.scalars(request));
        return new Object[]{201, out};
    }

    private ApprovalRule matchRule(String documentType, boolean filterType, BigDecimal amount) {
        String jpql = "select r from ApprovalRule r where r.isActive = true and r.minAmount <= :amt"
                + (filterType ? " and r.documentType = :t" : "") + " order by r.minAmount desc";
        TypedQuery<ApprovalRule> q = em.createQuery(jpql, ApprovalRule.class).setParameter("amt", amount);
        if (filterType) {
            q.setParameter("t", documentType);
        }
        List<ApprovalRule> rows = q.setMaxResults(1).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static JsonNode field(JsonNode body, String key) {
        JsonNode v = body == null ? null : body.get(key);
        return v == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : v;
    }

    /** Nilai string wajib (Prisma menolak undefined/null/non-string untuk kolom String wajib). */
    private static String requiredText(JsonNode body, String key, String failed) {
        JsonNode v = field(body, key);
        if (!v.isTextual()) {
            throw ErpException.of(500, failed);
        }
        return v.textValue();
    }

    /** Field opsional: absen/teks boleh; null hanya bila kolomnya nullable; tipe lain ditolak Prisma. */
    private static boolean invalidText(JsonNode v, boolean nullable) {
        if (v.isMissingNode() || v.isTextual()) {
            return false;
        }
        return !(nullable && v.isNull());
    }
}
