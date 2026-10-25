package id.neravoxa.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.approval.ErpApprovalHandler;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Surat Jalan: stok keluar per gudang dengan unit cost WAC terkunci + metadata akun HPP (tanpa GL),
 * edit (balik + terapkan ulang), batal (stok kembali), validasi Zod, gerbang stok minus → approval.
 */
class DeliveryOrderFlowTest extends FulfillmentTestSupport {

    private Map<String, Object> doBody(String partyId, Object... lines) {
        return map("date", today(), "partyId", partyId, "items", List.of(lines));
    }

    private Map<String, Object> line(String itemId, String name, Object qty) {
        return map("inventoryItemId", itemId, "itemName", name, "quantity", qty, "unit", "kg");
    }

    @Test
    void submit_locks_wac_cost_without_gl_then_edit_and_cancel_restore_stock() throws Exception {
        adminUserId();
        String party = customer(uniq("Cust DO"));
        String itemName = uniq("Beras");
        String item = item(itemName, "10000", "100");
        BigDecimal cogsBefore = balance("5.1");
        BigDecimal invBefore = balance("1.4.0");

        Map<String, Object> body = doBody(party, line(item, itemName, 30),
                map("itemName", "Ongkos bongkar", "quantity", "1", "unit", "ls"));
        body.put("nomorMobil", "B 1234 XY");
        body.put("notes", "");
        Result r = call(post(DO, body));
        assertEquals(201, r.status(), String.valueOf(r.body()));
        JsonNode d = r.data();
        String doId = d.get("id").asText();
        String doNumber = d.get("doNumber").asText();
        assertTrue(doNumber.startsWith("DO-"), doNumber);
        assertEquals("Submitted", d.get("status").asText());
        assertTrue(d.get("notes").isNull());
        assertEquals(2, d.get("items").size());
        assertEquals(party, d.get("customer").get("id").asText());
        assertAmount("70", itemStock(item));
        assertAmount("70", whStock(item));

        Map<String, Object> sm = jdbc.queryForMap("SELECT * FROM stock_movements WHERE reference_type = 'DeliveryOrder' "
                + "AND reference_id = ?", doId);
        assertEquals("Out", sm.get("movement_type"));
        assertAmount("10000", sm.get("unit_cost"));
        assertAmount("300000", sm.get("total_value"));
        assertEquals(accountId("5.1"), sm.get("offset_account_id"));
        assertEquals("Auto dari " + doNumber, sm.get("notes"));
        // Tanpa dampak GL: invoice yang memposting HPP.
        assertAmount("0", balance("5.1").subtract(cogsBefore));
        assertAmount("0", balance("1.4.0").subtract(invBefore));

        JsonNode det = ok(get(DO + "/" + doId));
        JsonNode first = det.get("items").get(0);
        assertEquals(itemName, first.get("inventoryItem").get("name").asText());
        assertEquals("GDG-UTAMA", first.get("warehouse").get("code").asText());
        assertEquals(sm.get("id"), first.get("stockMovement").get("id").asText());
        JsonNode second = det.get("items").get(1);
        assertTrue(second.get("inventoryItem").isNull());
        assertTrue(second.get("stockMovement").isNull());
        assertTrue(det.get("invoice").isNull());
        assertTrue(det.get("salesOrder").isNull());
        assertTrue(det.get("user").has("fullName"));
        assertEquals("Surat jalan tidak ditemukan.", call(get(DO + "/" + UUID.randomUUID())).error());

        JsonNode list = ok(get(DO + "?search=" + doNumber));
        assertEquals(1, list.get("total").asInt());
        JsonNode row = list.get("data").get(0);
        assertEquals(2, row.get("_count").get("items").asInt());
        assertEquals(party, row.get("customer").get("id").asText());
        assertFalse(row.get("customer").has("address"));
        assertEquals(0, ok(get(DO + "?status=Invoiced&partyId=" + party)).get("total").asInt());
        assertEquals(1, ok(get(DO + "?status=Submitted&partyId=" + party)).get("total").asInt());
        assertEquals(1, ok(get(DO + "?partyId=" + party + "&search=b 1234")).get("total").asInt());
        assertEquals("Status surat jalan tidak dikenal.", call(get(DO + "?status=Foo")).error());
        assertEquals("Format tanggal harus YYYY-MM-DD.", call(get(DO + "?startDate=2026-13-01")).error());

        JsonNode pending = ok(get(DO + "/pending?partyId=" + party));
        assertEquals(1, pending.size());
        assertEquals(itemName, pending.get(0).get("items").get(0).get("inventoryItem").get("name").asText());
        assertTrue(pending.get(0).get("items").get(0).get("salesOrderItem").isNull());
        assertEquals("partyId wajib diisi.", call(get(DO + "/pending")).error());

        JsonNode patched = ok(patch(DO + "/" + doId, map("driverName", "Pak Budi")));
        assertEquals("Pak Budi", patched.get("driverName").asText());
        assertEquals("B 1234 XY", patched.get("nomorMobil").asText());

        // Edit penuh: gerakan lama dibalik, baris baru (qty 20) diterapkan; nomor DO tetap.
        JsonNode edited = ok(put(DO + "/" + doId, doBody(party, line(item, itemName, 20))));
        assertEquals(doNumber, edited.get("doNumber").asText());
        assertEquals(1, edited.get("items").size());
        assertAmount("80", itemStock(item));
        assertAmount("80", whStock(item));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE reference_id = ? AND is_cancelled = true",
                Integer.class, doId));
        assertEquals("Auto dari " + doNumber + " (edited)", jdbc.queryForObject(
                "SELECT notes FROM stock_movements WHERE reference_id = ? AND is_cancelled = false", String.class, doId));

        JsonNode cancelled = ok(post(DO + "/" + doId + "/cancel", map()));
        assertEquals("Cancelled", cancelled.get("status").asText());
        assertAmount("100", itemStock(item));
        assertAmount("100", whStock(item));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE reference_id = ? AND is_cancelled = false",
                Integer.class, doId));
        assertEquals("Surat jalan sudah dibatalkan.", call(post(DO + "/" + doId + "/cancel", map())).error());
        assertEquals("Surat jalan sudah dibatalkan dan tidak bisa diedit.",
                call(put(DO + "/" + doId, doBody(party, line(item, itemName, 1)))).error());
        assertEquals("Hanya surat jalan berstatus Submitted yang bisa diedit.",
                call(patch(DO + "/" + doId, map("notes", "x"))).error());
    }

    @Test
    void validation_stock_shortage_and_negative_stock_approval_gate() throws Exception {
        String adminId = adminUserId();
        String party = customer(uniq("Cust Neg"));
        String itemName = uniq("Jagung");
        String item = item(itemName, "1000", "5");

        assertEquals("Invalid input: expected string, received undefined",
                call(post(DO, map("partyId", party, "items", List.of()))).error());
        assertEquals("Customer wajib dipilih.", call(post(DO, doBody("bukan-uuid", line(item, itemName, 1)))).error());
        assertEquals("Minimal satu item.", call(post(DO, doBody(party))).error());
        assertEquals("Jumlah harus lebih dari 0.", call(post(DO, doBody(party, line(item, itemName, 0)))).error());
        assertEquals("Invalid input: expected number, received NaN",
                call(post(DO, doBody(party, line(item, itemName, "abc")))).error());
        Map<String, Object> shortReason = doBody(party, line(item, itemName, 10));
        shortReason.put("allowNegativeStock", true);
        shortReason.put("negativeStockReason", "pendek");
        assertEquals("Alasan bypass stok minus wajib diisi (minimal 10 karakter).", call(post(DO, shortReason)).error());

        Result shortage = call(post(DO, doBody(party, line(item, itemName, 10))));
        assertEquals(400, shortage.status());
        assertEquals("Stok '" + itemName + "' di gudang Gudang Utama tidak cukup. Tersedia: 5 kg, dibutuhkan: 10 kg. "
                + "Buat produksi/transfer/penerimaan dulu.", shortage.error());

        // Staf tanpa capability stok minus: tidak diblok, masuk antrian approval (202).
        String email = "gudang." + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
        asUser(email, "ERP_PRODUCTION");
        Map<String, Object> negative = doBody(party, line(item, itemName, 10));
        negative.put("allowNegativeStock", true);
        negative.put("negativeStockReason", "Stok fisik ada, belum diinput produksi");
        Result queued = call(post(DO, negative));
        assertEquals(202, queued.status(), String.valueOf(queued.body()));
        assertTrue(queued.data().get("pendingApproval").asBoolean());
        assertEquals("Surat jalan menunggu persetujuan manajer (stok minus).", queued.data().get("message").asText());
        JsonNode req = queued.data().get("request");
        assertEquals("DeliveryOrder", req.get("documentType").asText());
        assertEquals("Surat Jalan (menunggu approval, stok minus)", req.get("documentNumber").asText());
        assertFalse(req.has("requester"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM delivery_orders WHERE party_id = ?", Integer.class, party));

        Result forbidden = call(put(DO + "/" + UUID.randomUUID(), negative));
        assertEquals(403, forbidden.status());
        assertEquals("Hanya Admin yang dapat bypass validasi stok.", forbidden.error());
        assertEquals(403, call(post(DO + "/" + UUID.randomUUID() + "/cancel", map())).status());

        // Disetujui: dibuat atas nama pengaju dengan bypass stok minus.
        String requesterId = userId(email);
        ErpApprovalHandler.ApprovedDocument doc = approve(req.get("id").asText(), adminId);
        assertTrue(doc.number().startsWith("DO-"), doc.number());
        assertEquals(requesterId, jdbc.queryForObject("SELECT created_by FROM delivery_orders WHERE id = ?", String.class, doc.id()));
        assertAmount("-5", itemStock(item));
        assertAmount("-5", whStock(item));

        // Pemegang capability: bypass langsung (201).
        asAdmin();
        Result direct = call(post(DO, negative));
        assertEquals(201, direct.status(), String.valueOf(direct.body()));
        assertAmount("-15", itemStock(item));
    }
}
