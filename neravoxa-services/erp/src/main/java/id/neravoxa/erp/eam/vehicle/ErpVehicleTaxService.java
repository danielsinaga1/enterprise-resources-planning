package id.neravoxa.erp.eam.vehicle;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.entity.GaDocument;
import id.neravoxa.erp.entity.GaVehicleTax;
import id.neravoxa.erp.entity.GaVehicleTaxRequest;
import id.neravoxa.erp.entity.GaVehicleTaxRequestStatus;
import id.neravoxa.erp.entity.GaVehicleTaxStatus;
import id.neravoxa.erp.entity.GaVehicleTaxType;
import id.neravoxa.erp.entity.GaWorkOrderPriority;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pajak, STNK dan KIR kendaraan (riwayat per tahun) + pengajuan pajak ber-approval. Approve pengajuan
 * otomatis membuat record pajak berstatus IN_PROCESS yang lalu masuk antrian Tagihan EAM (dibayar lewat
 * modul Pengajuan Pembayaran — tidak diduplikasi di sini).
 */
@Service
public class ErpVehicleTaxService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    static Map<String, Object> documentRef(GaDocument d) {
        return d == null ? null : ErpJson.map("id", d.getId(), "code", d.getCode(), "title", d.getTitle());
    }

    private static ObjectNode plain(GaVehicleTax t) {
        ObjectNode o = ErpJson.obj(t);
        o.remove(List.of("asset", "document", "request", "journalEntry", "purchaseInvoice", "account", "creditAccount"));
        return o;
    }

    @ErpTx(readOnly = true)
    public List<ObjectNode> list(String assetId) {
        return em.createQuery("select t from GaVehicleTax t left join fetch t.document where t.assetId = :a "
                        + "order by t.dueDate desc", GaVehicleTax.class)
                .setParameter("a", assetId).getResultList().stream()
                .map(t -> ErpJson.merge(plain(t), ErpJson.map("document", documentRef(t.getDocument()))))
                .toList();
    }

    private ObjectNode reload(String id) {
        em.flush();
        em.clear();
        return plain(em.find(GaVehicleTax.class, id));
    }

    @ErpTx
    public ObjectNode create(String assetId, VehicleRequests.Tax in, String userId) {
        GaVehicleTax t = new GaVehicleTax();
        t.setAssetId(assetId);
        t.setType(GaVehicleTaxType.valueOf(in.type()));
        t.setPeriodYear(in.periodYear());
        t.setAmount(in.amount());
        t.setPaidAt(in.paidAt() != null && !in.paidAt().isEmpty() ? ErpDates.toInstant(in.paidAt()) : null);
        t.setDueDate(ErpDates.toInstant(in.dueDate()));
        t.setStatus(GaVehicleTaxStatus.valueOf(in.status()));
        t.setDocumentId(in.documentId());
        t.setNotes(in.notes());
        t.setCreatedById(userId);
        em.persist(t);
        return reload(t.getId());
    }

    @ErpTx
    public ObjectNode update(String taxId, VehicleRequests.Tax in) {
        GaVehicleTax current = em.find(GaVehicleTax.class, taxId);
        if (current == null) {
            throw ErpException.notFound("Data pajak tidak ditemukan.");
        }
        // Guard keuangan: record yang sudah masuk antrian/pembukuan (IN_PROCESS / terjurnal / berfaktur)
        // TIDAK boleh diturunkan statusnya ke ACTIVE — tagihannya akan hilang dari antrian Tagihan GA.
        boolean terkunci = current.getStatus() == GaVehicleTaxStatus.IN_PROCESS
                || current.getJournalEntryId() != null
                || current.getPurchaseInvoiceId() != null;
        String currentStatus = current.getStatus() == null ? null : current.getStatus().name();
        // Minta ganti status padahal terkunci → tolak eksplisit, jangan diam-diam dilewati.
        if (in.status() != null && !in.status().equals(currentStatus) && terkunci) {
            String alasan = current.getJournalEntryId() != null
                    ? "sudah terjurnal di pembukuan"
                    : current.getPurchaseInvoiceId() != null
                    ? "sudah punya faktur pembelian"
                    : "sudah masuk antrian Tagihan GA (IN_PROCESS)";
            throw ErpException.conflict("Status tidak bisa diubah ke " + in.status() + ": data pajak ini " + alasan
                    + ". Batalkan dulu jurnal/invoice terkait di menu Tagihan GA, baru status bisa diubah.");
        }
        if (in.type() != null) {
            current.setType(GaVehicleTaxType.valueOf(in.type()));
        }
        if (in.present().test("periodYear")) {
            current.setPeriodYear(in.periodYear());
        }
        if (in.present().test("amount")) {
            current.setAmount(in.amount());
        }
        if (in.present().test("paidAt")) {
            current.setPaidAt(in.paidAt() != null && !in.paidAt().isEmpty() ? ErpDates.toInstant(in.paidAt()) : null);
        }
        if (in.dueDate() != null) {
            current.setDueDate(ErpDates.toInstant(in.dueDate()));
        }
        if (in.status() != null && !terkunci) {
            current.setStatus(GaVehicleTaxStatus.valueOf(in.status()));
        }
        if (in.present().test("documentId")) {
            current.setDocumentId(connectDocument(em, in.documentId()));
        }
        if (in.present().test("notes")) {
            current.setNotes(in.notes());
        }
        return reload(taxId);
    }

    /** {@code connect} Prisma: dokumen harus ada (P2025); string kosong/null = disconnect. */
    static String connectDocument(EntityManager em, String documentId) {
        if (documentId == null || documentId.isEmpty()) {
            return null;
        }
        if (em.find(GaDocument.class, documentId) == null) {
            throw new EntityNotFoundException();
        }
        return documentId;
    }

    @ErpTx
    public void delete(String taxId) {
        GaVehicleTax t = em.find(GaVehicleTax.class, taxId);
        if (t == null) {
            throw new EntityNotFoundException();
        }
        em.remove(t);
        em.flush();
    }

    // Pengajuan pajak/STNK/KIR

    @ErpTx
    public Map<String, Object> createRequest(String assetId, VehicleRequests.TaxRequest in, String userId) {
        GaVehicleTaxRequest r = new GaVehicleTaxRequest();
        r.setAssetId(assetId);
        r.setRequestedById(userId);
        r.setType(GaVehicleTaxType.valueOf(in.type()));
        r.setPeriodYear(in.periodYear());
        r.setEstimatedAmount(in.estimatedAmount());
        r.setDueDate(ErpDates.toInstant(in.dueDate()));
        r.setDeskripsi(in.deskripsi());
        r.setUrgensi(GaWorkOrderPriority.valueOf(in.urgensi()));
        r.setStatus(GaVehicleTaxRequestStatus.SUBMITTED);
        em.persist(r);
        em.flush();
        return ErpVehicleServiceRequestService.idStatus(r.getId(), r.getStatus());
    }

    private static ObjectNode plainRequest(GaVehicleTaxRequest r) {
        ObjectNode o = ErpJson.obj(r);
        o.remove(List.of("asset", "tax"));
        return o;
    }

    private static final Comparator<GaVehicleTaxRequest> REQUEST_ORDER = Comparator
            .comparing((GaVehicleTaxRequest r) -> r.getStatus().ordinal())
            .thenComparing(r -> -r.getUrgensi().ordinal())
            .thenComparing(GaVehicleTaxRequest::getCreatedAt, Comparator.reverseOrder());

    @ErpTx(readOnly = true)
    public List<ObjectNode> listRequests(String status, String assetId, String mineUserId) {
        GaVehicleTaxRequestStatus st = ErpVehicleServiceRequestService.statusParam(
                GaVehicleTaxRequestStatus.class, status, "Gagal mengambil pengajuan pajak.");
        StringBuilder jpql = new StringBuilder("select r from GaVehicleTaxRequest r left join fetch r.asset "
                + "left join fetch r.tax where 1=1");
        if (st != null) {
            jpql.append(" and r.status = :st");
        }
        if (assetId != null) {
            jpql.append(" and r.assetId = :asset");
        }
        if (mineUserId != null) {
            jpql.append(" and r.requestedById = :me");
        }
        var q = em.createQuery(jpql.toString(), GaVehicleTaxRequest.class);
        if (st != null) {
            q.setParameter("st", st);
        }
        if (assetId != null) {
            q.setParameter("asset", assetId);
        }
        if (mineUserId != null) {
            q.setParameter("me", mineUserId);
        }
        return q.getResultList().stream().sorted(REQUEST_ORDER).map(r -> {
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("asset", ErpVehicleServiceRequestService.assetRef(r.getAsset()));
            GaVehicleTax t = r.getTax();
            extra.put("tax", t == null ? null : ErpJson.map("id", t.getId(), "status", t.getStatus(), "periodYear", t.getPeriodYear()));
            return ErpJson.merge(plainRequest(r), extra);
        }).toList();
    }

    @ErpTx(readOnly = true)
    public ObjectNode requestDetail(String id) {
        List<GaVehicleTaxRequest> rows = em.createQuery("select r from GaVehicleTaxRequest r left join fetch r.asset "
                        + "left join fetch r.tax where r.id = :id", GaVehicleTaxRequest.class)
                .setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.notFound("Pengajuan tidak ditemukan.");
        }
        GaVehicleTaxRequest r = rows.get(0);
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("asset", ErpVehicleServiceRequestService.assetWithVehicle(em, r.getAsset()));
        extra.put("tax", r.getTax() == null ? null : plain(r.getTax()));
        return ErpJson.merge(plainRequest(r), extra);
    }

    /** Approve — buat record pajak IN_PROCESS lalu tautkan (atomik). */
    @ErpTx
    public Map<String, Object> approveRequest(String id, String userId) {
        GaVehicleTaxRequest tr = em.find(GaVehicleTaxRequest.class, id);
        if (tr == null) {
            throw ErpException.business("Pengajuan tidak ditemukan.");
        }
        if (tr.getStatus() != GaVehicleTaxRequestStatus.SUBMITTED) {
            throw ErpException.business("Tidak bisa approve dari status " + tr.getStatus() + ".");
        }
        GaVehicleTax tax = new GaVehicleTax();
        tax.setAssetId(tr.getAssetId());
        tax.setType(tr.getType());
        tax.setPeriodYear(tr.getPeriodYear());
        tax.setAmount(tr.getEstimatedAmount());
        tax.setDueDate(tr.getDueDate());
        tax.setStatus(GaVehicleTaxStatus.IN_PROCESS);
        tax.setNotes(tr.getDeskripsi() != null && !tr.getDeskripsi().isEmpty()
                ? "Dari pengajuan: " + tr.getDeskripsi() : "Dibuat dari pengajuan pajak.");
        tax.setCreatedById(tr.getRequestedById());
        em.persist(tax);
        em.flush();

        tr.setStatus(GaVehicleTaxRequestStatus.APPROVED);
        tr.setApprovedById(userId);
        tr.setApprovedAt(Instant.now());
        tr.setTaxId(tax.getId());
        em.flush();
        Map<String, Object> out = ErpVehicleServiceRequestService.idStatus(tr.getId(), tr.getStatus());
        out.put("taxId", tax.getId());
        return out;
    }

    @ErpTx
    public Map<String, Object> rejectRequest(String id, String reason, String userId) {
        GaVehicleTaxRequest tr = em.find(GaVehicleTaxRequest.class, id);
        if (tr == null) {
            throw ErpException.notFound("Pengajuan tidak ditemukan.");
        }
        if (tr.getStatus() != GaVehicleTaxRequestStatus.SUBMITTED) {
            throw ErpException.business("Tidak bisa reject dari status " + tr.getStatus() + ".");
        }
        tr.setStatus(GaVehicleTaxRequestStatus.REJECTED);
        tr.setApprovedById(userId);
        tr.setApprovedAt(Instant.now());
        tr.setRejectedReason(reason);
        em.flush();
        return ErpVehicleServiceRequestService.idStatus(tr.getId(), tr.getStatus());
    }

    @ErpTx
    public Map<String, Object> cancelRequest(String id, String userId) {
        GaVehicleTaxRequest tr = em.find(GaVehicleTaxRequest.class, id);
        if (tr == null) {
            throw ErpException.notFound("Pengajuan tidak ditemukan.");
        }
        if (!tr.getRequestedById().equals(userId)) {
            throw ErpException.forbidden("Hanya pengaju yang bisa membatalkan.");
        }
        if (tr.getStatus() != GaVehicleTaxRequestStatus.SUBMITTED) {
            throw ErpException.business("Tidak bisa cancel dari status " + tr.getStatus() + ".");
        }
        tr.setStatus(GaVehicleTaxRequestStatus.CANCELLED);
        em.flush();
        return ErpVehicleServiceRequestService.idStatus(tr.getId(), tr.getStatus());
    }
}
