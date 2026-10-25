package id.neravoxa.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Surat Jalan Sementara: penomoran SJS-YYYYMM-NNNN, tanpa efek stok, tautan ke DO saat submit /
 * tautan ke DO yang sudah ada, kembali Terbuka saat DO batal, guard edit/batal.
 */
class TemporaryDeliveryNoteFlowTest extends FulfillmentTestSupport {

    private Map<String, Object> noteBody(String party, String itemId, Object qty) {
        return map("date", today(), "partyId", party, "nomorMobil", "B 9 ZZ",
                "items", List.of(map("inventoryItemId", itemId, "itemName", "nama bebas", "quantity", qty)));
    }

    private String status(String noteId) {
        return jdbc.queryForObject("SELECT status FROM temporary_delivery_notes WHERE id = ?", String.class, noteId);
    }

    @Test
    void lifecycle_link_on_delivery_order_and_reopen_on_cancel() throws Exception {
        adminUserId();
        String party = customer(uniq("Cust SJS"));
        String other = customer(uniq("Cust SJS Lain"));
        String itemName = uniq("Gabah");
        String item = item(itemName, "4000", "50");

        Result created = call(post(TDN, noteBody(party, item, 4)));
        assertEquals(201, created.status(), String.valueOf(created.body()));
        JsonNode note = created.data();
        String noteId = note.get("id").asText();
        String number = note.get("number").asText();
        assertTrue(number.matches("SJS-\\d{6}-\\d{4}"), number);
        assertEquals("Open", note.get("status").asText());
        assertEquals(itemName, note.get("items").get(0).get("itemName").asText());
        assertEquals("kg", note.get("items").get(0).get("unit").asText());
        assertAmount("4", note.get("items").get(0).get("quantity"));
        assertTrue(note.get("customerName").asText().startsWith("Cust SJS"));
        assertTrue(note.get("date").asText().endsWith("T00:00:00.000Z"), note.get("date").asText());
        assertAmount("50", itemStock(item));

        JsonNode list = ok(get(TDN + "?partyId=" + party));
        assertEquals(1, list.get("total").asInt());
        JsonNode row = list.get("data").get(0);
        assertEquals(itemName + " 4 kg", row.get("ringkasan").asText());
        assertTrue(row.has("umur_hari"));
        assertEquals(0, ok(get(TDN + "?partyId=" + party + "&status=Linked")).get("total").asInt());
        assertEquals(1, ok(get(TDN + "?search=" + number)).get("total").asInt());
        assertEquals(1, ok(get(TDN + "?partyId=" + party + "&dateFrom=" + today() + "&sort=-number")).get("total").asInt());
        assertEquals("Status surat jalan sementara tidak dikenal.", call(get(TDN + "?status=Foo")).error());

        JsonNode linkable = ok(get(TDN + "/linkable?partyId=" + party));
        assertEquals(1, linkable.size());
        assertAmount("4", linkable.get(0).get("items").get(0).get("quantity"));
        assertEquals(0, ok(get(TDN + "/linkable")).size());
        JsonNode summary = ok(get(TDN + "/summary?days=0"));
        assertTrue(summary.get("open").asInt() >= 1);
        assertEquals(7, summary.get("days").asInt());

        JsonNode updated = ok(put(TDN + "/" + noteId, noteBody(party, item, 6)));
        assertAmount("6", updated.get("items").get(0).get("quantity"));

        // DO dengan temporaryNoteIds → SJS Linked; SJS milik customer lain ditolak.
        Map<String, Object> doBody = map("date", today(), "partyId", party, "temporaryNoteIds", List.of(noteId),
                "items", List.of(map("inventoryItemId", item, "itemName", itemName, "quantity", 6, "unit", "kg")));
        JsonNode d = ok(post(DO, doBody));
        assertEquals("Linked", status(noteId));
        JsonNode linked = ok(get(TDN + "/" + noteId));
        assertEquals(d.get("doNumber").asText(), linked.get("doNumber").asText());
        assertTrue(linked.get("linkedAt").asText().endsWith("Z"));
        assertEquals("Sudah ditautkan ke surat jalan resmi, tidak bisa dibatalkan. Batalkan surat jalan resmi dulu.",
                call(post(TDN + "/" + noteId + "/cancel", map())).error());
        assertEquals("Hanya surat jalan sementara berstatus Terbuka yang bisa diubah.",
                call(put(TDN + "/" + noteId, noteBody(party, item, 1))).error());
        assertEquals("Surat jalan sementara " + number + " sudah tidak berstatus Terbuka.",
                call(post(DO, doBody)).error());

        // DO dibatalkan → SJS kembali Terbuka.
        ok(post(DO + "/" + d.get("id").asText() + "/cancel", map()));
        assertEquals("Open", status(noteId));
        assertEquals("Surat jalan resmi " + d.get("doNumber").asText() + " tidak aktif (status Cancelled).",
                call(post(TDN + "/" + noteId + "/link", map("deliveryOrderId", d.get("id").asText()))).error());

        // Tautkan ke DO yang sudah ada.
        doBody.remove("temporaryNoteIds");
        JsonNode d2 = ok(post(DO, doBody));
        JsonNode relinked = ok(post(TDN + "/" + noteId + "/link", map("deliveryOrderId", d2.get("id").asText())));
        assertEquals("Linked", relinked.get("status").asText());
        assertEquals(d2.get("doNumber").asText(), relinked.get("doNumber").asText());

        JsonNode otherNote = ok(post(TDN, noteBody(other, item, 1)));
        assertEquals("Surat jalan sementara " + otherNote.get("number").asText() + " milik customer lain.",
                call(post(TDN + "/" + otherNote.get("id").asText() + "/link", map("deliveryOrderId", d2.get("id").asText()))).error());
        JsonNode cancelled = ok(post(TDN + "/" + otherNote.get("id").asText() + "/cancel", map()));
        assertEquals("Cancelled", cancelled.get("status").asText());
        assertEquals("Surat jalan sementara sudah dibatalkan.",
                call(post(TDN + "/" + otherNote.get("id").asText() + "/cancel", map())).error());
        assertEquals("Ada surat jalan sementara yang tidak ditemukan.", call(post(DO, map("date", today(), "partyId", party,
                "temporaryNoteIds", List.of(UUID.randomUUID().toString()),
                "items", List.of(map("itemName", "Jasa", "quantity", 1, "unit", "ls"))))).error());
    }

    @Test
    void validation_messages() throws Exception {
        adminUserId();
        String party = customer(uniq("Cust SJS V"));
        String item = item(uniq("Kedelai"), "1000", "0");
        assertEquals("Invalid input: expected number, received string",
                call(post(TDN, noteBody(party, item, "4"))).error());
        assertEquals("Qty harus lebih dari 0.", call(post(TDN, noteBody(party, item, 0))).error());
        assertEquals("Invalid input: expected string, received undefined", call(post(TDN, map("date", today(),
                "partyId", party, "items", List.of(map("quantity", 1))))).error());
        assertEquals("Minimal satu item.", call(post(TDN, map("date", today(), "partyId", party, "items", List.of()))).error());
        assertEquals("Barang tidak ditemukan di master item.",
                call(post(TDN, noteBody(party, UUID.randomUUID().toString(), 1))).error());
        assertEquals("Pesanan penjualan tidak ditemukan.", call(post(TDN, map("date", today(), "partyId", party,
                "salesOrderId", UUID.randomUUID().toString(),
                "items", List.of(map("inventoryItemId", item, "quantity", 1))))).error());
        assertEquals("Too small: expected string to have >=1 characters",
                call(post(TDN + "/" + UUID.randomUUID() + "/link", map("deliveryOrderId", ""))).error());
        assertEquals("Surat jalan sementara tidak ditemukan.", call(get(TDN + "/" + UUID.randomUUID())).error());
        assertEquals(404, call(get(TDN + "/" + UUID.randomUUID())).status());

        asUser("staf.sjs." + UUID.randomUUID().toString().substring(0, 8) + "@test.local", "ERP_PRODUCTION");
        assertEquals(403, call(post(TDN + "/" + UUID.randomUUID() + "/cancel", map())).status());
    }
}
