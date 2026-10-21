package id.enterpriseapps.erp.production;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CRUD master mesin, proses produksi dan standar biaya (kontrak & pesan ENTARIS). */
class ProductionMasterDataTest extends ProductionTestSupport {

    static final String MACHINES = "/api/erp/inventory/machines";
    static final String PROCESSES = "/api/erp/inventory/production-processes";
    static final String STANDARDS = "/api/erp/inventory/production-cost-standards";

    @Test
    void machine_crud_unique_code_and_delete_guard() throws Exception {
        asAdmin();
        String code = "MSN-" + uid();
        Result created = call(post(MACHINES, map("code", "  " + code + " ", "name", " Huller A ", "idealRatePerHour", "120.5")));
        assertEquals(201, created.status(), String.valueOf(created.body()));
        JsonNode m = created.data();
        assertEquals(code, m.get("code").asText());
        assertEquals("Huller A", m.get("name").asText());
        assertEquals("kg", m.get("unit").asText());
        assertTrue(m.get("isActive").asBoolean());
        assertNum("120.5", m.get("idealRatePerHour"));
        String id = m.get("id").asText();

        Result dup = call(post(MACHINES, map("code", code, "name", "Lain")));
        assertEquals(409, dup.status());
        assertEquals("Kode mesin \"" + code + "\" sudah ada.", dup.error());

        Result invalid = call(post(MACHINES, map("code", "", "name", "X")));
        assertEquals(400, invalid.status());
        assertEquals("Kode mesin wajib diisi.", invalid.error());
        Result negative = call(post(MACHINES, map("code", "N-" + uid(), "name", "X", "idealRatePerHour", -1)));
        assertEquals("Kapasitas ideal tidak boleh negatif.", negative.error());

        assertEquals(id, ok(get(MACHINES + "/" + id)).get("id").asText());
        Result missing = call(get(MACHINES + "/00000000-0000-4000-8000-000000000000"));
        assertEquals(404, missing.status());
        assertEquals("Mesin tidak ditemukan.", missing.error());

        // PUT parsial: hanya field yang dikirim.
        JsonNode upd = ok(put(MACHINES + "/" + id, map("isActive", false, "notes", "servis")));
        assertFalse(upd.get("isActive").asBoolean());
        assertEquals("servis", upd.get("notes").asText());
        assertEquals("Huller A", upd.get("name").asText());
        assertNum("120.5", upd.get("idealRatePerHour"));
        Result nullName = call(put(MACHINES + "/" + id, map("name", null)));
        assertEquals(400, nullName.status());
        assertEquals("Invalid input: expected string, received null", nullName.error());

        JsonNode inactive = ok(get(MACHINES + "?active=false"));
        assertTrue(contains(inactive.get("data"), id));
        assertFalse(contains(ok(get(MACHINES + "?active=true")).get("data"), id));
        assertTrue(contains(ok(get(MACHINES)).get("data"), id));

        // Dipakai downtime run → 409, mesin bebas → dihapus.
        String runId = "run-" + uid();
        jdbc.update("INSERT INTO production_runs (id, run_number, date, fiscal_year_id, created_by_id, updated_at) "
                        + "VALUES (?, ?, now(), (SELECT id FROM fiscal_years WHERE is_closed = false LIMIT 1), "
                        + "(SELECT id FROM users LIMIT 1), now())",
                runId, "PR-T-" + uid());
        jdbc.update("INSERT INTO production_downtimes (id, production_run_id, machine_id, category, duration_min) "
                + "VALUES (?, ?, ?, 'Breakdown', 15)", "dt-" + uid(), runId, id);
        Result blocked = call(delete(MACHINES + "/" + id));
        assertEquals(409, blocked.status());
        assertEquals("Mesin tidak bisa dihapus karena sudah dipakai di 0 proses produksi dan 1 catatan downtime. "
                + "Nonaktifkan mesin saja agar histori OEE tetap utuh.", blocked.error());

        String free = ok(post(MACHINES, map("code", "F-" + uid(), "name", "Bebas"))).get("id").asText();
        JsonNode del = ok(delete(MACHINES + "/" + free));
        assertEquals("Mesin dihapus.", del.get("message").asText());
        assertTrue(del.get("deleted").asBoolean());
        assertEquals(404, call(delete(MACHINES + "/" + free)).status());
    }

    @Test
    void process_crud_and_soft_delete_when_used() throws Exception {
        asAdmin();
        String name = "Giling " + uid();
        JsonNode p = call(post(PROCESSES, map("name", " " + name + " ", "description", "proses giling", "sortOrder", "3"))).data();
        assertEquals(name, p.get("name").asText());
        assertEquals(3, p.get("sortOrder").asInt());
        String id = p.get("id").asText();

        Result dup = call(post(PROCESSES, map("name", name)));
        assertEquals(409, dup.status());
        assertEquals("Proses \"" + name + "\" sudah ada.", dup.error());
        assertEquals("Nama proses wajib diisi.", call(post(PROCESSES, map("name", ""))).error());
        assertEquals("Invalid input: expected int, received number", call(post(PROCESSES, map("name", "X" + uid(), "sortOrder", 1.5))).error());

        JsonNode upd = ok(put(PROCESSES + "/" + id, map("isActive", false)));
        assertFalse(upd.get("isActive").asBoolean());
        assertEquals(3, upd.get("sortOrder").asInt());
        assertEquals(404, call(put(PROCESSES + "/00000000-0000-4000-8000-000000000000", map("name", "Y"))).status());
        assertTrue(contains(ok(get(PROCESSES + "?active=false")).get("data"), id));

        // Hapus butuh doc.cancel.
        asUser("prod.proc." + uid().toLowerCase() + "@test.local", "ERP_PRODUCTION");
        assertEquals(403, call(delete(PROCESSES + "/" + id)).status());

        asAdmin();
        JsonNode del = ok(delete(PROCESSES + "/" + id));
        assertTrue(del.get("deleted").asBoolean());
        assertEquals("Proses produksi dihapus.", del.get("message").asText());

        String used = ok(post(PROCESSES, map("name", "Sortir " + uid()))).get("id").asText();
        String runId = "run-" + uid();
        jdbc.update("INSERT INTO production_runs (id, run_number, date, fiscal_year_id, created_by_id, updated_at) "
                        + "VALUES (?, ?, now(), (SELECT id FROM fiscal_years WHERE is_closed = false LIMIT 1), "
                        + "(SELECT id FROM users LIMIT 1), now())", runId, "PR-T-" + uid());
        jdbc.update("INSERT INTO production_run_processes (id, production_run_id, process_id) VALUES (?, ?, ?)",
                "rp-" + uid(), runId, used);
        JsonNode soft = ok(delete(PROCESSES + "/" + used));
        assertTrue(soft.get("deactivated").asBoolean());
        assertEquals("Proses dinonaktifkan karena sudah dipakai di 1 run produksi.", soft.get("message").asText());
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT is_active FROM production_processes WHERE id = ?", Boolean.class, used));
    }

    @Test
    void cost_standard_crud_filters_and_includes() throws Exception {
        asAdmin();
        String itemId = item("Gabah Std");
        String accId = jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = '5.12'", String.class);
        String name = "Biaya giling " + uid();
        Result created = call(post(STANDARDS, map("name", name, "costSide", "InputSide", "basis", "InputQty",
                "defaultRate", "150", "costAccountId", accId, "itemId", itemId)));
        assertEquals(201, created.status(), String.valueOf(created.body()));
        JsonNode s = created.data();
        assertEquals("5.12", s.get("costAccount").get("accountNumber").asText());
        assertEquals(itemId, s.get("item").get("id").asText());
        assertEquals("kg", s.get("item").get("unit").asText());
        assertTrue(s.get("warehouse").isNull());
        assertNum("150", s.get("defaultRate"));
        String id = s.get("id").asText();

        String global = ok(post(STANDARDS, map("name", "Biaya global " + uid(), "costSide", "OutputSide", "basis", "Fixed",
                "defaultRate", 1000))).get("id").asText();

        assertEquals("Invalid option: expected one of \"InputSide\"|\"OutputSide\"",
                call(post(STANDARDS, map("name", "X", "costSide", "Lain", "basis", "Fixed", "defaultRate", 1))).error());
        assertEquals("Rate tidak boleh negatif.",
                call(post(STANDARDS, map("name", "X", "costSide", "InputSide", "basis", "Fixed", "defaultRate", -5))).error());
        assertEquals("Referensi data tidak valid. Periksa data yang dipilih.",
                call(post(STANDARDS, map("name", "X", "costSide", "InputSide", "basis", "Fixed", "defaultRate", 1,
                        "itemId", "00000000-0000-4000-8000-000000000000"))).error());

        // Filter item mencocokkan standar item tsb + standar global (item NULL).
        JsonNode byItem = ok(get(STANDARDS + "?itemId=" + itemId)).get("data");
        assertTrue(contains(byItem, id));
        assertTrue(contains(byItem, global));
        JsonNode otherItem = ok(get(STANDARDS + "?itemId=" + item("Lain Std"))).get("data");
        assertFalse(contains(otherItem, id));
        assertTrue(contains(otherItem, global));
        assertFalse(contains(ok(get(STANDARDS + "?costSide=OutputSide")).get("data"), id));

        JsonNode upd = ok(put(STANDARDS + "/" + id, map("defaultRate", 175, "warehouseId", wh())));
        assertNum("175", upd.get("defaultRate"));
        assertEquals("GDG-UTAMA", upd.get("warehouse").get("code").asText());
        assertEquals(name, upd.get("name").asText());
        Result notFound = call(put(STANDARDS + "/00000000-0000-4000-8000-000000000000", map("name", "Y")));
        assertEquals(404, notFound.status());
        assertEquals("Standar biaya produksi tidak ditemukan.", notFound.error());

        JsonNode del = ok(delete(STANDARDS + "/" + global));
        assertTrue(del.get("deleted").asBoolean());
        assertEquals("Standar biaya produksi dihapus.", del.get("message").asText());
    }

    static boolean contains(JsonNode rows, String id) {
        for (JsonNode r : rows) {
            if (id.equals(r.get("id").asText())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static List<String> ids(JsonNode rows) {
        List<String> out = new java.util.ArrayList<>();
        rows.forEach(r -> out.add(r.get("id").asText()));
        return out;
    }
}
