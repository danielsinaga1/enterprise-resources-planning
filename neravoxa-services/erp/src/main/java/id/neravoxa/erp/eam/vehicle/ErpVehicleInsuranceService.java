package id.neravoxa.erp.eam.vehicle;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.entity.GaVehicleInsurance;
import id.neravoxa.erp.entity.GaVehicleInsuranceStatus;
import id.neravoxa.erp.entity.GaVehicleInsuranceType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.util.List;

/** Asuransi kendaraan (1-n, riwayat polis). */
@Service
public class ErpVehicleInsuranceService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private static ObjectNode plain(GaVehicleInsurance i) {
        ObjectNode o = ErpJson.obj(i);
        o.remove(List.of("asset", "document"));
        return o;
    }

    private ObjectNode reload(String id) {
        em.flush();
        em.clear();
        return plain(em.find(GaVehicleInsurance.class, id));
    }

    @ErpTx(readOnly = true)
    public List<ObjectNode> list(String assetId) {
        return em.createQuery("select i from GaVehicleInsurance i left join fetch i.document where i.assetId = :a "
                        + "order by i.endDate desc", GaVehicleInsurance.class)
                .setParameter("a", assetId).getResultList().stream()
                .map(i -> ErpJson.merge(plain(i), ErpJson.map("document", ErpVehicleTaxService.documentRef(i.getDocument()))))
                .toList();
    }

    @ErpTx
    public ObjectNode create(String assetId, VehicleRequests.Insurance in, String userId) {
        GaVehicleInsurance i = new GaVehicleInsurance();
        i.setAssetId(assetId);
        i.setType(GaVehicleInsuranceType.valueOf(in.type()));
        i.setProvider(in.provider());
        i.setPolicyNumber(in.policyNumber());
        i.setPremium(in.premium());
        i.setCoverageValue(in.coverageValue());
        i.setStartDate(ErpDates.toInstant(in.startDate()));
        i.setEndDate(ErpDates.toInstant(in.endDate()));
        i.setStatus(GaVehicleInsuranceStatus.valueOf(in.status()));
        i.setDocumentId(in.documentId());
        i.setNotes(in.notes());
        i.setCreatedById(userId);
        em.persist(i);
        return reload(i.getId());
    }

    @ErpTx
    public ObjectNode update(String id, VehicleRequests.Insurance in) {
        GaVehicleInsurance i = em.find(GaVehicleInsurance.class, id);
        if (i == null) {
            throw new EntityNotFoundException();
        }
        if (in.type() != null) {
            i.setType(GaVehicleInsuranceType.valueOf(in.type()));
        }
        if (in.provider() != null) {
            i.setProvider(in.provider());
        }
        if (in.present().test("policyNumber")) {
            i.setPolicyNumber(in.policyNumber());
        }
        if (in.present().test("premium")) {
            i.setPremium(in.premium());
        }
        if (in.present().test("coverageValue")) {
            i.setCoverageValue(in.coverageValue());
        }
        if (in.startDate() != null) {
            i.setStartDate(ErpDates.toInstant(in.startDate()));
        }
        if (in.endDate() != null) {
            i.setEndDate(ErpDates.toInstant(in.endDate()));
        }
        // Zod 4: default 'ACTIVE' tetap terisi walau skemanya partial.
        if (in.status() != null) {
            i.setStatus(GaVehicleInsuranceStatus.valueOf(in.status()));
        }
        if (in.present().test("documentId")) {
            i.setDocumentId(ErpVehicleTaxService.connectDocument(em, in.documentId()));
        }
        if (in.present().test("notes")) {
            i.setNotes(in.notes());
        }
        return reload(id);
    }

    @ErpTx
    public void delete(String id) {
        GaVehicleInsurance i = em.find(GaVehicleInsurance.class, id);
        if (i == null) {
            throw new EntityNotFoundException();
        }
        em.remove(i);
        em.flush();
    }
}
