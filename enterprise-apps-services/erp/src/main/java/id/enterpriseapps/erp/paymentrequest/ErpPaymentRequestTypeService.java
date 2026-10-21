package id.enterpriseapps.erp.paymentrequest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.entity.Account;
import id.enterpriseapps.erp.entity.PaymentRequestType;
import id.enterpriseapps.erp.paymentrequest.PrRequests.TypeInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jenis Pengajuan Pembayaran (master data) — logika {@code routes/paymentRequestTypes.ts}.
 * Jenis yang sudah dipakai tidak boleh dihapus (nonaktifkan agar riwayat tetap utuh).
 */
@Service
@RequiredArgsConstructor
public class ErpPaymentRequestTypeService {

    /** Pesan P2025 Prisma (baris yang di-update/hapus tidak ada). */
    static final String RECORD_NOT_FOUND = "Data terkait tidak ditemukan. Periksa referensi yang dipilih.";

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /** {@code include: { defaultExpenseAccount: { select: { id, accountNumber, name } } }}. */
    private ObjectNode json(PaymentRequestType t) {
        ObjectNode o = PrJson.scalars(t);
        Account acc = t.getDefaultExpenseAccountId() == null ? null : em.find(Account.class, t.getDefaultExpenseAccountId());
        o.set("defaultExpenseAccount", ErpJson.tree(PrJson.accountRef(acc)));
        return o;
    }

    private void assertExpenseAccount(String accountId) {
        if (accountId == null) {
            return;
        }
        Account acc = em.find(Account.class, accountId);
        if (acc == null || Boolean.TRUE.equals(acc.getIsGroup()) || !Boolean.TRUE.equals(acc.getIsActive())) {
            throw ErpException.business("Akun biaya default harus akun detail yang aktif.");
        }
    }

    private static void apply(PaymentRequestType t, TypeInput body) {
        t.setCode(body.code());
        t.setName(body.name());
        t.setKind(body.kind());
        t.setDescription(body.description() == null || body.description().isEmpty() ? null : body.description());
        t.setDefaultExpenseAccountId(body.defaultExpenseAccountId());
        t.setRequiresAttachment(body.requiresAttachment());
        t.setRequiresSettlement(body.requiresSettlement());
        t.setIsActive(body.isActive());
        t.setSortOrder(body.sortOrder());
    }

    /** GET / — jenis aktif (formulir); {@code all} = juga yang nonaktif (pengaturan). */
    @ErpTx(readOnly = true)
    public List<ObjectNode> list(boolean all) {
        List<PaymentRequestType> types = em.createQuery("select t from PaymentRequestType t"
                        + (all ? "" : " where t.isActive = true") + " order by t.sortOrder asc, t.name asc",
                        PaymentRequestType.class)
                .getResultList();
        Map<String, Long> counts = new HashMap<>();
        em.createQuery("select p.typeId, count(p) from PaymentRequest p group by p.typeId", Object[].class)
                .getResultList()
                .forEach(r -> counts.put((String) r[0], ((Number) r[1]).longValue()));
        List<ObjectNode> out = new ArrayList<>();
        for (PaymentRequestType t : types) {
            ObjectNode o = json(t);
            o.set("_count", ErpJson.tree(Map.of("requests", counts.getOrDefault(t.getId(), 0L))));
            out.add(o);
        }
        return out;
    }

    @ErpTx
    public ObjectNode create(TypeInput body) {
        assertExpenseAccount(body.defaultExpenseAccountId());
        Long exists = em.createQuery("select count(t) from PaymentRequestType t where t.code = :code", Long.class)
                .setParameter("code", body.code()).getSingleResult();
        if (exists > 0) {
            throw ErpException.business("Kode jenis pengajuan sudah dipakai.");
        }
        PaymentRequestType t = new PaymentRequestType();
        apply(t, body);
        em.persist(t);
        em.flush();
        return json(t);
    }

    @ErpTx
    public ObjectNode update(String id, TypeInput body) {
        assertExpenseAccount(body.defaultExpenseAccountId());
        Long dup = em.createQuery("select count(t) from PaymentRequestType t where t.code = :code and t.id <> :id", Long.class)
                .setParameter("code", body.code()).setParameter("id", id).getSingleResult();
        if (dup > 0) {
            throw ErpException.business("Kode jenis pengajuan sudah dipakai.");
        }
        PaymentRequestType t = em.find(PaymentRequestType.class, id);
        if (t == null) {
            throw ErpException.business(RECORD_NOT_FOUND);
        }
        apply(t, body);
        em.flush();
        return json(t);
    }

    /** DELETE /:id — hanya jenis yang belum dipakai; yang sudah dipakai sebaiknya dinonaktifkan. */
    @ErpTx
    public Map<String, Object> delete(String id) {
        Long used = em.createQuery("select count(p) from PaymentRequest p where p.typeId = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (used > 0) {
            throw ErpException.business("Jenis ini sudah dipakai " + used
                    + " pengajuan. Nonaktifkan saja agar riwayat tetap utuh.");
        }
        PaymentRequestType t = em.find(PaymentRequestType.class, id);
        if (t == null) {
            throw ErpException.business(RECORD_NOT_FOUND);
        }
        em.remove(t);
        em.flush();
        return Map.of("ok", true);
    }
}
