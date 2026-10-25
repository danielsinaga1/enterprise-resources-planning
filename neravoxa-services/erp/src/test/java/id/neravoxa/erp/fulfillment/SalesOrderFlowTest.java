package id.neravoxa.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Siklus Pesanan Penjualan: Draft → edit → submit → generate-do (parsial & lebih kirim) dengan qty
 * terkirim + status diturunkan dari baris DO, edit/batal DO menghitung ulang SO, guard batal SO.
 */
class SalesOrderFlowTest extends FulfillmentTestSupport {

    private BigDecimalAssert delivered(String soItemId) {
        return new BigDecimalAssert(jdbc.queryForObject("SELECT delivered_qty FROM sales_order_items WHERE id = ?",
                java.math.BigDecimal.class, soItemId));
    }

    private record BigDecimalAssert(java.math.BigDecimal value) {
        void is(String expected) {
            assertAmount(expected, value);
        }
    }

    private String soStatus(String soId) {
        return jdbc.queryForObject("SELECT status FROM sales_orders WHERE id = ?", String.class, soId);
    }

    @Test
    void lifecycle_generate_do_derives_delivered_qty_and_status() throws Exception {
        adminUserId();
        String party = customer(uniq("Cust SO"));
        String nameA = uniq("Beras SO");
        String itemA = item(nameA, "5000", "100");
        String refNo = uniq("PO-CUST");

        Map<String, Object> body = map("date", today(), "partyId", party, "customerRefNo", refNo,
                "potongan", 10000, "biayaLain", "5000", "taxPct", 11,
                "items", List.of(
                        map("inventoryItemId", itemA, "itemName", nameA, "quantity", 10, "unit", "kg", "rate", 12000,
                                "discount", 0, "amount", 120000),
                        map("itemName", "Jasa kirim", "quantity", 1, "unit", "ls", "rate", 50000, "amount", 50000)));
        Result created = call(post(SO, body));
        assertEquals(201, created.status(), String.valueOf(created.body()));
        JsonNode so = created.data();
        String soId = so.get("id").asText();
        assertTrue(so.get("soNumber").asText().startsWith("PSN-"));
        assertEquals("Draft", so.get("status").asText());
        assertAmount("170000", so.get("subtotal"));
        // (170.000 − 10.000) + PPN 11% + biaya lain 5.000
        assertAmount("182600", so.get("grandTotal"));
        assertEquals(2, so.get("items").size());
        assertEquals(party, so.get("customer").get("id").asText());

        // Edit Draft: item diganti, tautan penawaran (tidak dikirim) tetap null.
        body.put("items", List.of(
                map("inventoryItemId", itemA, "itemName", nameA, "quantity", 20, "unit", "kg", "rate", 12000, "amount", 240000),
                map("itemName", "Jasa kirim", "quantity", 1, "unit", "ls", "rate", 50000, "amount", 50000)));
        JsonNode updated = ok(put(SO + "/" + soId, body));
        assertAmount("290000", updated.get("subtotal"));
        assertTrue(updated.get("quotationId").isNull());
        String soItemA = updated.get("items").get(0).get("id").asText();
        assertEquals(nameA, updated.get("items").get(0).get("itemName").asText());
        String soItemB = updated.get("items").get(1).get("id").asText();

        Map<String, Object> gen = map("date", today(), "items", List.of(map("salesOrderItemId", soItemA, "quantity", 8)));
        assertEquals("Hanya SO status Submitted atau PartiallyDelivered yang bisa generate Surat Jalan.",
                call(post(SO + "/" + soId + "/generate-do", gen)).error());

        JsonNode submitted = ok(post(SO + "/" + soId + "/submit", map()));
        assertEquals("Submitted", submitted.get("status").asText());
        assertFalse(submitted.get("submittedAt").isNull());
        assertEquals(2, submitted.get("items").size());
        assertEquals("Hanya SO status Draft yang bisa di-submit.", call(post(SO + "/" + soId + "/submit", map())).error());
        assertEquals("Sales Order yang sudah Submitted tidak bisa diedit. Cancel dulu kalau perlu.",
                call(put(SO + "/" + soId, body)).error());

        // Generate DO parsial.
        Result g1 = call(post(SO + "/" + soId + "/generate-do", gen));
        assertEquals(201, g1.status(), String.valueOf(g1.body()));
        JsonNode do1 = g1.data();
        String do1Id = do1.get("id").asText();
        assertEquals(soId, do1.get("salesOrder").get("id").asText());
        assertEquals(so.get("soNumber").asText(), do1.get("salesOrder").get("soNumber").asText());
        assertEquals(soItemA, do1.get("items").get(0).get("salesOrderItemId").asText());
        assertEquals("Auto dari SO " + so.get("soNumber").asText(), do1.get("notes").asText());
        assertEquals("PartiallyDelivered", soStatus(soId));
        delivered(soItemA).is("8");
        assertAmount("92", itemStock(itemA));

        // Generate DO kedua: lebih kirim diperbolehkan (15 > sisa 12) + baris jasa.
        JsonNode do2 = ok(post(SO + "/" + soId + "/generate-do", map("date", today(), "notes", "Kirim kedua",
                "items", List.of(map("salesOrderItemId", soItemA, "quantity", 15), map("salesOrderItemId", soItemB, "quantity", 1)))));
        String do2Id = do2.get("id").asText();
        assertEquals("FullyDelivered", soStatus(soId));
        delivered(soItemA).is("23");
        delivered(soItemB).is("1");
        assertTrue(do2.get("items").get(1).get("inventoryItemId").isNull());

        JsonNode detail = ok(get(SO + "/" + soId));
        assertEquals(2, detail.get("deliveryOrders").size());
        assertEquals("kg", detail.get("deliveryOrders").get(0).get("items").get(0).get("unit").asText());
        assertTrue(detail.get("deliveryOrders").get(0).get("invoice").isNull());
        assertEquals(nameA, detail.get("items").get(0).get("inventoryItem").get("name").asText());
        assertTrue(detail.get("quotation").isNull());
        assertEquals(0, detail.get("depositPayments").size());
        assertAmount("0", detail.get("depositSummary").get("received"));
        assertEquals(0, detail.get("depositSummary").get("pct").asInt());
        assertAmount(detail.get("grandTotal").decimalValue().toPlainString(), detail.get("depositSummary").get("remaining"));

        JsonNode list = ok(get(SO + "?search=" + refNo));
        assertEquals(1, list.get("total").asInt());
        assertEquals(2, list.get("data").get(0).get("_count").get("items").asInt());
        assertEquals(2, list.get("data").get(0).get("_count").get("deliveryOrders").asInt());
        assertEquals(1, ok(get(SO + "?partyId=" + party + "&sort=-expectedDelivery")).get("total").asInt());
        JsonNode summary = ok(get(SO + "/summary?partyId=" + party));
        assertEquals(1, summary.get("count").asInt());
        assertEquals(1, summary.get("fullyDeliveredCount").asInt());
        assertAmount(detail.get("grandTotal").decimalValue().toPlainString(), summary.get("activeValue"));
        assertEquals(1, summary.get("byStatus").get("FullyDelivered").get("count").asInt());

        assertEquals("Sales Order punya 2 Surat Jalan aktif. Batalkan SJ dulu sebelum cancel SO.",
                call(post(SO + "/" + soId + "/cancel", map())).error());

        // Batal DO kedua → qty terkirim & status diturunkan ulang.
        ok(post(DO + "/" + do2Id + "/cancel", map()));
        assertEquals("PartiallyDelivered", soStatus(soId));
        delivered(soItemA).is("8");
        delivered(soItemB).is("0");

        // Edit DO pertama (baris dibuat ulang) → tautan SO dipulihkan + hitung ulang.
        JsonNode editedDo = ok(put(DO + "/" + do1Id, map("date", today(), "partyId", party,
                "items", List.of(map("inventoryItemId", itemA, "itemName", nameA, "quantity", 5, "unit", "kg")))));
        assertEquals(soItemA, editedDo.get("items").get(0).get("salesOrderItemId").asText());
        delivered(soItemA).is("5");
        assertEquals("PartiallyDelivered", soStatus(soId));

        ok(post(DO + "/" + do1Id + "/cancel", map()));
        assertEquals("Submitted", soStatus(soId));
        delivered(soItemA).is("0");
        assertAmount("100", itemStock(itemA));

        JsonNode soCancelled = ok(post(SO + "/" + soId + "/cancel", map()));
        assertEquals("Cancelled", soCancelled.get("status").asText());
        assertFalse(soCancelled.has("items"));
        assertEquals("Sales Order sudah dibatalkan.", call(post(SO + "/" + soId + "/cancel", map())).error());
    }

    @Test
    void validation_links_and_capability_checks() throws Exception {
        adminUserId();
        String party = customer(uniq("Cust SO2"));
        assertEquals("Status pesanan tidak dikenal.", call(get(SO + "?status=Foo")).error());
        assertEquals("Format tanggal harus YYYY-MM-DD.", call(get(SO + "/summary?endDate=19-10-2026")).error());
        assertEquals("Sales Order tidak ditemukan.", call(get(SO + "/" + UUID.randomUUID())).error());

        Map<String, Object> line = map("itemName", "Jasa", "quantity", 1, "unit", "ls", "rate", 1000, "amount", 1000);
        assertEquals("Too small: expected array to have >=1 items",
                call(post(SO, map("date", today(), "partyId", party, "items", List.of()))).error());
        assertEquals("Invalid input: expected number, received NaN", call(post(SO, map("date", today(), "partyId", party,
                "items", List.of(map("itemName", "Jasa", "quantity", 1, "unit", "ls", "rate", 1000))))).error());
        assertEquals("Too big: expected number to be <=100",
                call(post(SO, map("date", today(), "partyId", party, "taxPct", 101, "items", List.of(line)))).error());
        assertEquals("Penawaran tidak ditemukan.", call(post(SO, map("date", today(), "partyId", party,
                "quotationId", UUID.randomUUID().toString(), "items", List.of(line)))).error());

        JsonNode so = ok(post(SO, map("date", today(), "partyId", party, "items", List.of(line))));
        String soId = so.get("id").asText();
        ok(post(SO + "/" + soId + "/submit", map()));
        assertEquals("SO item x-1 tidak ditemukan di SO ini.", call(post(SO + "/" + soId + "/generate-do",
                map("date", today(), "items", List.of(map("salesOrderItemId", "x-1", "quantity", 1))))).error());

        asUser("staf.so." + UUID.randomUUID().toString().substring(0, 8) + "@test.local", "ERP_PRODUCTION");
        Result forbidden = call(post(SO + "/" + soId + "/generate-do", map("date", today(), "allowNegativeStock", true,
                "items", List.of(map("salesOrderItemId", so.get("items").get(0).get("id").asText(), "quantity", 1)))));
        assertEquals(403, forbidden.status());
        assertEquals("Hanya Admin yang dapat bypass validasi stok.", forbidden.error());
    }
}
