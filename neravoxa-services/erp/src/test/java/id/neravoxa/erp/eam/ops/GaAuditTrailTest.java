package id.neravoxa.erp.eam.ops;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.audit.ErpAuditService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Jejak audit modul GA: tipe entitas per sumber daya dan snapshot sebelum perubahan untuk WO. */
class GaAuditTrailTest extends GaOpsTestSupport {

    @Test
    void ga_entity_types_are_resource_paths() {
        assertEquals("ga/maintenance/work-orders", ErpAuditService.entityType("ga", "maintenance", "work-orders",
                "8d5c6a2e-1b1f-4b6e-9a51-3f6f0d0b6a11", "assign"));
        assertEquals("ga/assets", ErpAuditService.entityType("ga", "assets", "8d5c6a2e-1b1f-4b6e-9a51-3f6f0d0b6a11"));
        assertEquals("ga/material-requests", ErpAuditService.entityType("ga", "material-requests", "smp-eam-m01", "approve"));
        assertEquals("ga/vehicle/service-logs", ErpAuditService.entityType("ga", "vehicle", "service-logs"));
        assertEquals("sales", ErpAuditService.entityType("sales", "invoices"));
    }

    @Test
    void work_order_assignment_records_before_and_after() throws Exception {
        String admin = adminId();
        asAdmin();
        String t = tag();
        String woId = ok(post(MT + "/work-orders", body("assetId", asset(t + "A", "MESIN_PRODUKSI"), "type", "CORRECTIVE",
                "title", "Audit " + t))).get("id").asText();
        ok(patch(MT + "/work-orders/" + woId + "/assign", body("assignedToId", admin)));

        Map<String, Object> row = jdbc.queryForMap("SELECT entity_type, action, old_values::text AS old, new_values::text AS new "
                + "FROM audit_trail WHERE entity_id = ? AND action = 'UPDATE' ORDER BY created_at DESC LIMIT 1", woId);
        assertEquals("ga/maintenance/work-orders", row.get("entity_type"));
        JsonNode before = ErpJson.mapper().readTree((String) row.get("old"));
        JsonNode after = ErpJson.mapper().readTree((String) row.get("new"));
        assertEquals("OPEN", before.get("status").asText());
        assertEquals("ASSIGNED", after.get("status").asText());
        assertEquals("ga/maintenance/work-orders", jdbc.queryForObject(
                "SELECT entity_type FROM audit_trail WHERE entity_id = ? AND action = 'CREATE'", String.class, woId));
    }
}
