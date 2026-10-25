package id.neravoxa.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Master data GA: CRUD per entitas, validasi bergaya Zod, P2002/P2003/P2025, include cabang. */
class GaMasterDataTest extends EamAssetTestSupport {

    private static boolean containsId(JsonNode arr, String id) {
        for (JsonNode n : arr) {
            if (id.equals(n.get("id").asText())) {
                return true;
            }
        }
        return false;
    }

    private static JsonNode byId(JsonNode arr, String id) {
        for (JsonNode n : arr) {
            if (id.equals(n.get("id").asText())) {
                return n;
            }
        }
        throw new AssertionError("tidak ada " + id);
    }

    @Test
    void branches_cost_centers_and_locations_crud() throws Exception {
        asAdmin();
        String code = uniq("BR");
        Result created = call(post(MASTER + "/branches", map("code", code, "name", "Cabang Uji", "address", "Jl. A")));
        assertEquals(201, created.status());
        JsonNode br = created.data();
        assertEquals(code, br.get("code").asText());
        assertTrue(br.get("isActive").asBoolean());
        assertEquals("Jl. A", br.get("address").asText());
        String brId = br.get("id").asText();

        expectError(400, "Kode wajib diisi.", post(MASTER + "/branches", map("code", "", "name", "X")));
        expectError(400, "Invalid input: expected string, received undefined", post(MASTER + "/branches", map("code", "X")));
        expectError(400, "Data duplikat terdeteksi. Silakan coba lagi.",
                post(MASTER + "/branches", map("code", code, "name", "Dup")));

        // Partial: isActive tidak di-reset ke default, address tetap.
        JsonNode upd = ok(put(MASTER + "/branches/" + brId, map("name", "Cabang Baru")));
        assertEquals("Cabang Baru", upd.get("name").asText());
        assertEquals("Jl. A", upd.get("address").asText());
        assertTrue(upd.get("isActive").asBoolean());
        upd = ok(put(MASTER + "/branches/" + brId, map("isActive", false)));
        assertFalse(upd.get("isActive").asBoolean());
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.",
                put(MASTER + "/branches/tidak-ada", map("name", "X")));
        assertTrue(containsId(ok(get(MASTER + "/branches")), brId));

        // Cost center + include cabang {code, name}.
        expectError(400, "Cabang wajib dipilih.",
                post(MASTER + "/cost-centers", map("code", uniq("CC"), "name", "CC", "branchId", "")));
        JsonNode cc = ok(post(MASTER + "/cost-centers", map("code", uniq("CC"), "name", "Produksi", "branchId", brId)));
        JsonNode ccRow = byId(ok(get(MASTER + "/cost-centers")), cc.get("id").asText());
        assertEquals(code, ccRow.at("/branch/code").asText());
        assertEquals(2, ccRow.get("branch").size());

        // Lokasi (tanpa isActive) + include cabang.
        JsonNode loc = ok(post(MASTER + "/locations", map("code", uniq("LOC"), "name", "Rak A", "branchId", brId)));
        assertFalse(loc.has("isActive"));
        JsonNode locRow = byId(ok(get(MASTER + "/locations")), loc.get("id").asText());
        assertEquals("Cabang Baru", locRow.at("/branch/name").asText());

        // Cabang masih dirujuk → P2003.
        expectError(400, "Referensi data tidak valid. Periksa data yang dipilih.", delete(MASTER + "/branches/" + brId));

        assertEquals("Lokasi dihapus.", ok(delete(MASTER + "/locations/" + loc.get("id").asText())).get("message").asText());
        assertEquals("Cost center dihapus.", ok(delete(MASTER + "/cost-centers/" + cc.get("id").asText())).get("message").asText());
        assertEquals("Cabang dihapus.", ok(delete(MASTER + "/branches/" + brId)).get("message").asText());
        expectError(400, "Data terkait tidak ditemukan. Periksa referensi yang dipilih.", delete(MASTER + "/branches/" + brId));
    }

    @Test
    void categories_document_types_and_inventory_categories() throws Exception {
        asAdmin();
        // Kategori aset + skema JSON; null pada update = tidak diubah.
        Map<String, Object> schema = map("fields", java.util.List.of(map("key", "warna", "label", "Warna")));
        JsonNode cat = ok(post(MASTER + "/asset-categories", map("code", uniq("CAT"), "name", "Kategori Uji",
                "customFieldsSchema", schema)));
        String catId = cat.get("id").asText();
        assertEquals("warna", cat.at("/customFieldsSchema/fields/0/key").asText());
        JsonNode catUpd = ok(put(MASTER + "/asset-categories/" + catId, map("name", "Kategori Ganti", "customFieldsSchema", null)));
        assertEquals("warna", catUpd.at("/customFieldsSchema/fields/0/key").asText());
        expectError(400, "Invalid input: expected record, received string",
                put(MASTER + "/asset-categories/" + catId, map("customFieldsSchema", "x")));
        assertTrue(containsId(ok(get(MASTER + "/asset-categories")), catId));

        // Jenis dokumen: category absen saat update ikut dikosongkan (perilaku sumber).
        JsonNode type = ok(post(MASTER + "/document-types", map("code", uniq("DT"), "name", "Izin", "category", "LEGAL")));
        String typeId = type.get("id").asText();
        assertEquals("LEGAL", type.get("category").asText());
        JsonNode typeUpd = ok(put(MASTER + "/document-types/" + typeId, map("name", "Izin Usaha")));
        assertTrue(typeUpd.get("category").isNull());
        assertTrue(containsId(ok(get(MASTER + "/document-types")), typeId));

        // Kategori inventory: enum type.
        expectError(400, "Invalid option: expected one of \"SPAREPART\"|\"CONSUMABLE\"",
                post(MASTER + "/inventory-categories", map("code", uniq("IC"), "name", "X", "type", "LAIN")));
        JsonNode ic = ok(post(MASTER + "/inventory-categories", map("code", uniq("IC"), "name", "Oli", "type", "CONSUMABLE")));
        assertEquals("CONSUMABLE", ic.get("type").asText());
        JsonNode icUpd = ok(put(MASTER + "/inventory-categories/" + ic.get("id").asText(), map("type", "SPAREPART")));
        assertEquals("SPAREPART", icUpd.get("type").asText());
        assertTrue(containsId(ok(get(MASTER + "/inventory-categories")), ic.get("id").asText()));

        assertEquals("Kategori inventory dihapus.",
                ok(delete(MASTER + "/inventory-categories/" + ic.get("id").asText())).get("message").asText());
        assertEquals("Jenis dokumen dihapus.", ok(delete(MASTER + "/document-types/" + typeId)).get("message").asText());
        assertEquals("Kategori aset dihapus.", ok(delete(MASTER + "/asset-categories/" + catId)).get("message").asText());
    }
}
