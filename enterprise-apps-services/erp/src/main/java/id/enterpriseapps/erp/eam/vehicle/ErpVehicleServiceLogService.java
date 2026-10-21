package id.enterpriseapps.erp.eam.vehicle;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpDates;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.ErpTx;
import id.enterpriseapps.erp.core.accounting.ErpVehicleServiceJournalService;
import id.enterpriseapps.erp.entity.Account;
import id.enterpriseapps.erp.entity.GaVehicleServiceLog;
import id.enterpriseapps.erp.entity.GaVehicleServiceType;
import id.enterpriseapps.erp.entity.GaWorkOrder;
import id.enterpriseapps.erp.entity.JournalEntry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Log servis kendaraan (1-n per aset) + integrasi GL. Posting/pembatalan jurnal memakai kernel
 * {@link ErpVehicleServiceJournalService} (DR beban 6.7 / CR kas-bank). Log yang sudah berjurnal aktif
 * tidak boleh diubah nilainya atau dihapus — GL akan tidak sinkron; batalkan jurnalnya dulu.
 */
@Service
@RequiredArgsConstructor
public class ErpVehicleServiceLogService {

    private final ErpVehicleServiceJournalService journals;
    private final ErpVehicleOdometerService odometer;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    static Map<String, Object> journalRef(JournalEntry je) {
        if (je == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", je.getId());
        m.put("entryNumber", je.getEntryNumber());
        m.put("status", je.getStatus());
        return m;
    }

    static Map<String, Object> accountRef(Account a) {
        if (a == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accountNumber", a.getAccountNumber());
        m.put("name", a.getName());
        return m;
    }

    private static Map<String, Object> workOrderRef(GaWorkOrder w) {
        if (w == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.getId());
        m.put("code", w.getCode());
        return m;
    }

    /** Scalar log + relasi berbentuk {@code select} Prisma. */
    private static ObjectNode withRelations(GaVehicleServiceLog log, boolean includeWorkOrder) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (includeWorkOrder) {
            extra.put("workOrder", workOrderRef(log.getWorkOrder()));
        }
        extra.put("journalEntry", journalRef(log.getJournalEntry()));
        extra.put("account", accountRef(log.getAccount()));
        extra.put("creditAccount", accountRef(log.getCreditAccount()));
        return ErpJson.merge(plain(log), extra);
    }

    /** Hanya kolom skalar (relasi apa pun yang kebetulan termuat dibuang). */
    private static ObjectNode plain(GaVehicleServiceLog log) {
        ObjectNode o = ErpJson.obj(log);
        o.remove(List.of("asset", "workOrder", "journalEntry", "purchaseInvoice", "account", "creditAccount",
                "serviceRequest"));
        return o;
    }

    private static final String FETCH = "select l from GaVehicleServiceLog l left join fetch l.workOrder "
            + "left join fetch l.journalEntry left join fetch l.account left join fetch l.creditAccount ";

    @ErpTx(readOnly = true)
    public List<ObjectNode> list(String assetId) {
        return em.createQuery(FETCH + "where l.assetId = :a order by l.tanggal desc", GaVehicleServiceLog.class)
                .setParameter("a", assetId).getResultList().stream()
                .map(l -> withRelations(l, true)).toList();
    }

    @ErpTx
    public ObjectNode create(String assetId, VehicleRequests.ServiceLog in, String userId) {
        boolean wantJournal = Boolean.TRUE.equals(in.postJournal());
        if (wantJournal && !(in.biaya() != null && in.biaya().signum() > 0)) {
            throw ErpException.business("Biaya harus > 0 untuk diposting ke jurnal.");
        }
        GaVehicleServiceLog log = new GaVehicleServiceLog();
        log.setAssetId(assetId);
        log.setTanggal(ErpDates.toInstant(in.tanggal()));
        log.setOdometer(in.odometer());
        log.setJenisService(GaVehicleServiceType.valueOf(in.jenisService()));
        log.setDeskripsi(in.deskripsi());
        log.setBengkel(in.bengkel());
        log.setBiaya(in.biaya());
        log.setSource("EAM");
        log.setCreatedById(userId);
        em.persist(log);
        em.flush();
        String id = log.getId();

        // Naikkan odometer terakhir (membuat baris data kendaraan bila belum ada).
        odometer.bump(assetId, in.odometer());

        if (wantJournal) {
            journals.post(id, in.accountId(), in.creditAccountId(), userId);
        }
        em.flush();
        em.clear();
        GaVehicleServiceLog fresh = em.createQuery(FETCH + "where l.id = :id", GaVehicleServiceLog.class)
                .setParameter("id", id).getSingleResult();
        return withRelations(fresh, false);
    }

    /** Posting menyusul untuk log yang belum berjurnal → {@code { message, journalEntry }}. */
    @ErpTx
    public Map<String, Object> postJournal(String serviceLogId, String accountId, String creditAccountId, String userId) {
        JournalEntry entry = journals.post(serviceLogId, accountId, creditAccountId, userId);
        em.flush();
        em.clear();
        JournalEntry fresh = em.createQuery(
                        "select j from JournalEntry j left join fetch j.items where j.id = :id", JournalEntry.class)
                .setParameter("id", entry.getId()).getSingleResult();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Jurnal " + fresh.getEntryNumber() + " dibuat.");
        out.put("journalEntry", fresh);
        return out;
    }

    /** Batalkan jurnal service log (soft-cancel + lepas tautan). */
    @ErpTx
    public Map<String, Object> cancelJournal(String serviceLogId) {
        JournalEntry entry = journals.cancel(serviceLogId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Jurnal " + entry.getEntryNumber() + " dibatalkan.");
        return out;
    }

    @ErpTx
    public ObjectNode update(String serviceLogId, VehicleRequests.ServiceLog in) {
        GaVehicleServiceLog log = em.find(GaVehicleServiceLog.class, serviceLogId);
        // Guard: log berjurnal aktif tidak boleh diubah nilainya — GL akan desync.
        if (log != null && log.getJournalEntryId() != null) {
            boolean touchesGl = in.present().test("biaya") || in.present().test("tanggal") || in.present().test("deskripsi");
            if (touchesGl) {
                throw ErpException.business(
                        "Service log ini sudah diposting ke jurnal. Batalkan jurnalnya dulu sebelum mengubah biaya/tanggal/deskripsi.");
            }
        }
        if (log == null) {
            throw new EntityNotFoundException();
        }
        if (in.tanggal() != null) {
            log.setTanggal(ErpDates.toInstant(in.tanggal()));
        }
        if (in.present().test("odometer")) {
            log.setOdometer(in.odometer());
        }
        if (in.jenisService() != null) {
            log.setJenisService(GaVehicleServiceType.valueOf(in.jenisService()));
        }
        if (in.deskripsi() != null) {
            log.setDeskripsi(in.deskripsi());
        }
        if (in.present().test("bengkel")) {
            log.setBengkel(in.bengkel());
        }
        if (in.present().test("biaya")) {
            log.setBiaya(in.biaya());
        }
        em.flush();
        em.clear();
        return plain(em.find(GaVehicleServiceLog.class, serviceLogId));
    }

    @ErpTx
    public void delete(String serviceLogId) {
        GaVehicleServiceLog log = em.find(GaVehicleServiceLog.class, serviceLogId);
        if (log != null && log.getJournalEntryId() != null) {
            throw ErpException.business(
                    "Service log ini sudah diposting ke jurnal. Batalkan jurnalnya dulu sebelum menghapus log.");
        }
        if (log == null) {
            throw new EntityNotFoundException();
        }
        em.remove(log);
        em.flush();
    }
}
