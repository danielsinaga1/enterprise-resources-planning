package id.neravoxa.erp.eam.vehicle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.security.ErpAccess;
import id.neravoxa.erp.entity.GaVehicleData;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Kendaraan GA — port {@code routes/ga/vehicle.ts} bagian data kendaraan, log &amp; pengajuan servis,
 * driver, pajak/STNK/KIR + pengajuan, dan asuransi (mount {@code /api/ga/vehicle}, gerbang modul
 * {@code ga_vehicle} dijalankan ErpRouteGuard). Log operasional ada di {@link ErpVehicleLogController},
 * daftar &amp; laporan armada di {@link ErpFleetController}.
 */
@RestController
@RequestMapping("/api/erp/ga/vehicle")
@RequiredArgsConstructor
public class ErpVehicleController {

    private final ErpAccess access;
    private final ErpVehicleDataService vehicleData;
    private final ErpVehicleServiceLogService serviceLogs;
    private final ErpVehicleServiceRequestService serviceRequests;
    private final ErpVehicleTaxService taxes;
    private final ErpVehicleInsuranceService insurances;

    private static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }

    private static String nonEmpty(Map<String, String> query, String key) {
        String v = query.get(key);
        return v == null || v.isEmpty() ? null : v;
    }

    // Data kendaraan (1-1 dengan aset)

    @GetMapping("/assets/{id}")
    public Object get(@PathVariable String id) {
        GaVehicleData data = vehicleData.get(id);
        return data == null ? NullNode.getInstance() : data;
    }

    @PutMapping("/assets/{id}")
    public GaVehicleData upsert(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        VehicleRequests.VehicleData in = VehicleRequests.VehicleData.parse(body);
        return vehicleData.upsert(id, in);
    }

    // Log servis

    @GetMapping("/assets/{id}/services")
    public List<ObjectNode> services(@PathVariable String id) {
        return serviceLogs.list(id);
    }

    @PostMapping("/assets/{id}/services")
    public ResponseEntity<ObjectNode> createService(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        VehicleRequests.ServiceLog in = VehicleRequests.ServiceLog.parse(body, false);
        return ResponseEntity.status(201).body(serviceLogs.create(id, in, access.userId()));
    }

    /** Posting jurnal menyusul untuk log yang belum berjurnal. */
    @PostMapping("/assets/{id}/services/{sid}/journal")
    public ResponseEntity<Map<String, Object>> postJournal(@PathVariable String id, @PathVariable String sid,
                                                           @RequestBody(required = false) JsonNode body) {
        return ResponseEntity.status(201).body(serviceLogs.postJournal(sid, ErpJson.text(body, "accountId"),
                ErpJson.text(body, "creditAccountId"), access.userId()));
    }

    /** Batalkan jurnal service log (soft-cancel + lepas tautan). */
    @DeleteMapping("/assets/{id}/services/{sid}/journal")
    public Map<String, Object> cancelJournal(@PathVariable String id, @PathVariable String sid) {
        return serviceLogs.cancelJournal(sid);
    }

    @PatchMapping("/assets/{id}/services/{sid}")
    public ObjectNode updateService(@PathVariable String id, @PathVariable String sid,
                                    @RequestBody(required = false) JsonNode body) {
        VehicleRequests.ServiceLog in = VehicleRequests.ServiceLog.parse(body, true);
        return serviceLogs.update(sid, in);
    }

    @DeleteMapping("/assets/{id}/services/{sid}")
    public ResponseEntity<Void> deleteService(@PathVariable String id, @PathVariable String sid) {
        serviceLogs.delete(sid);
        return noContent();
    }

    // Pengajuan servis

    @PostMapping("/assets/{id}/service-requests")
    public ResponseEntity<Map<String, Object>> createServiceRequest(@PathVariable String id,
                                                                    @RequestBody(required = false) JsonNode body) {
        VehicleRequests.ServiceRequest in = VehicleRequests.ServiceRequest.parse(body);
        return ResponseEntity.status(201).body(serviceRequests.create(id, in, access.userId()));
    }

    @GetMapping("/service-requests")
    public List<ObjectNode> listServiceRequests(@RequestParam Map<String, String> query) {
        return serviceRequests.list(nonEmpty(query, "status"), nonEmpty(query, "assetId"),
                "true".equals(query.get("mine")) ? access.userId() : null);
    }

    @GetMapping("/service-requests/{id}")
    public ObjectNode serviceRequest(@PathVariable String id) {
        return serviceRequests.detail(id);
    }

    @PatchMapping("/service-requests/{id}/approve")
    public Map<String, Object> approveServiceRequest(@PathVariable String id) {
        return serviceRequests.approve(id, access.userId());
    }

    @PatchMapping("/service-requests/{id}/reject")
    public Map<String, Object> rejectServiceRequest(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        String reason = VehicleRequests.parseReason(body);
        return serviceRequests.reject(id, reason, access.userId());
    }

    @PatchMapping("/service-requests/{id}/cancel")
    public Map<String, Object> cancelServiceRequest(@PathVariable String id) {
        return serviceRequests.cancel(id, access.userId());
    }

    // Driver (disimpan di GaAsset.picUserId)

    @PatchMapping("/assets/{id}/driver")
    public Map<String, Object> assignDriver(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        String picUserId = VehicleRequests.parseDriver(body);
        return vehicleData.assignDriver(id, picUserId);
    }

    // Pajak, STNK dan KIR

    @GetMapping("/assets/{id}/taxes")
    public List<ObjectNode> taxes(@PathVariable String id) {
        return taxes.list(id);
    }

    @PostMapping("/assets/{id}/taxes")
    public ResponseEntity<ObjectNode> createTax(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        VehicleRequests.Tax in = VehicleRequests.Tax.parse(body, false);
        return ResponseEntity.status(201).body(taxes.create(id, in, access.userId()));
    }

    @PatchMapping("/assets/{id}/taxes/{tid}")
    public ObjectNode updateTax(@PathVariable String id, @PathVariable String tid,
                                @RequestBody(required = false) JsonNode body) {
        VehicleRequests.Tax in = VehicleRequests.Tax.parse(body, true);
        return taxes.update(tid, in);
    }

    @DeleteMapping("/assets/{id}/taxes/{tid}")
    public ResponseEntity<Void> deleteTax(@PathVariable String id, @PathVariable String tid) {
        taxes.delete(tid);
        return noContent();
    }

    // Pengajuan pajak/STNK/KIR (approve → record pajak IN_PROCESS)

    @PostMapping("/assets/{id}/tax-requests")
    public ResponseEntity<Map<String, Object>> createTaxRequest(@PathVariable String id,
                                                                @RequestBody(required = false) JsonNode body) {
        VehicleRequests.TaxRequest in = VehicleRequests.TaxRequest.parse(body);
        return ResponseEntity.status(201).body(taxes.createRequest(id, in, access.userId()));
    }

    @GetMapping("/tax-requests")
    public List<ObjectNode> listTaxRequests(@RequestParam Map<String, String> query) {
        return taxes.listRequests(nonEmpty(query, "status"), nonEmpty(query, "assetId"),
                "true".equals(query.get("mine")) ? access.userId() : null);
    }

    @GetMapping("/tax-requests/{id}")
    public ObjectNode taxRequest(@PathVariable String id) {
        return taxes.requestDetail(id);
    }

    @PatchMapping("/tax-requests/{id}/approve")
    public Map<String, Object> approveTaxRequest(@PathVariable String id) {
        return taxes.approveRequest(id, access.userId());
    }

    @PatchMapping("/tax-requests/{id}/reject")
    public Map<String, Object> rejectTaxRequest(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        String reason = VehicleRequests.parseReason(body);
        return taxes.rejectRequest(id, reason, access.userId());
    }

    @PatchMapping("/tax-requests/{id}/cancel")
    public Map<String, Object> cancelTaxRequest(@PathVariable String id) {
        return taxes.cancelRequest(id, access.userId());
    }

    // Asuransi

    @GetMapping("/assets/{id}/insurances")
    public List<ObjectNode> insurances(@PathVariable String id) {
        return insurances.list(id);
    }

    @PostMapping("/assets/{id}/insurances")
    public ResponseEntity<ObjectNode> createInsurance(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        VehicleRequests.Insurance in = VehicleRequests.Insurance.parse(body, false);
        return ResponseEntity.status(201).body(insurances.create(id, in, access.userId()));
    }

    @PatchMapping("/assets/{id}/insurances/{iid}")
    public ObjectNode updateInsurance(@PathVariable String id, @PathVariable String iid,
                                      @RequestBody(required = false) JsonNode body) {
        VehicleRequests.Insurance in = VehicleRequests.Insurance.parse(body, true);
        return insurances.update(iid, in);
    }

    @DeleteMapping("/assets/{id}/insurances/{iid}")
    public ResponseEntity<Void> deleteInsurance(@PathVariable String id, @PathVariable String iid) {
        insurances.delete(iid);
        return noContent();
    }
}
