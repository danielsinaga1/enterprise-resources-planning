package id.neravoxa.erp.fulfillment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Body request modul pengiriman penjualan — port schema Zod {@code CreateSalesOrderSchema},
 * {@code GenerateDOFromSOSchema}, {@code CreateDeliveryOrderSchema}, {@code CreateDOFromInvoiceSchema},
 * {@code CreateSalesReturnSchema} (utils/schemas.ts) dan {@code TdnSchema} (routes/temporaryDeliveryNotes.ts).
 * Field dibaca sesuai urutan schema agar pesan error = issue pertama Zod.
 */
final class FulfillmentRequests {

    private FulfillmentRequests() {
    }

    // ───────────────────────────── Sales Order ─────────────────────────────

    /** {@code itemType}: product (dikirim lewat Surat Jalan) | service (jasa, langsung ditagih di invoice). */
    record SoItem(String inventoryItemId, String itemName, BigDecimal quantity, String unit, BigDecimal rate,
                  BigDecimal discount, BigDecimal amount, String description, String itemType, String serviceItemId) {
        boolean service() {
            return "service".equals(itemType);
        }
    }

    /**
     * @param quotationIdPresent field {@code quotationId} dikirim (undefined vs null dibedakan saat edit SO)
     */
    record SoInput(String date, String expectedDelivery, String partyId, String customerRefNo,
                   boolean quotationIdPresent, String quotationId, List<SoItem> items, String notes,
                   BigDecimal potongan, BigDecimal biayaLain, BigDecimal taxPct) {
    }

    /** {@code CreateSalesOrderSchema} (= UpdateSalesOrderSchema). */
    static SoInput salesOrder(JsonNode raw) {
        ObjectNode o = Zod.object(raw);
        String date = Zod.min(Zod.string(o, "date"), 1, null);
        String expectedDelivery = Zod.nullableString(o, "expectedDelivery");
        String partyId = Zod.min(Zod.string(o, "partyId"), 1, null);
        String customerRefNo = Zod.nullableString(o, "customerRefNo");
        boolean quotationPresent = Zod.present(o, "quotationId");
        String quotationId = Zod.nullableString(o, "quotationId");
        List<JsonNode> rawItems = Zod.array(o, "items");
        List<SoItem> items = new ArrayList<>();
        for (JsonNode el : rawItems) {
            ObjectNode it = Zod.element(el);
            String inventoryItemId = Zod.nullableString(it, "inventoryItemId");
            String itemName = Zod.min(Zod.string(it, "itemName"), 1, null);
            BigDecimal quantity = Zod.positive(Zod.coerceNumber(it, "quantity"), null);
            String unit = Zod.min(Zod.string(it, "unit"), 1, null);
            BigDecimal rate = Zod.gte(Zod.coerceNumber(it, "rate"), 0, null);
            BigDecimal discount = Zod.coerceNumberOr(it, "discount", BigDecimal.ZERO);
            Zod.lte(Zod.gte(discount, 0, null), 100, null);
            BigDecimal amount = Zod.gte(Zod.coerceNumber(it, "amount"), 0, null);
            String description = Zod.nullableString(it, "description");
            String itemType = Zod.nullableString(it, "itemType");
            if (itemType != null && !itemType.equals("product") && !itemType.equals("service")) {
                throw ErpException.business("Jenis item harus product atau service.");
            }
            boolean service = "service".equals(itemType);
            String serviceItemId = service ? Zod.truthy(Zod.nullableString(it, "serviceItemId")) : null;
            items.add(new SoItem(service ? null : Zod.truthy(inventoryItemId), itemName, quantity, unit, rate, discount, amount,
                    description, service ? "service" : "product", serviceItemId));
        }
        Zod.minItems(rawItems, 1, null);
        String notes = Zod.nullableString(o, "notes");
        BigDecimal potongan = Zod.gte(Zod.coerceNumberOr(o, "potongan", BigDecimal.ZERO), 0, null);
        BigDecimal biayaLain = Zod.coerceNumberOr(o, "biayaLain", BigDecimal.ZERO);
        BigDecimal taxPct = Zod.coerceNumberOr(o, "taxPct", BigDecimal.ZERO);
        Zod.lte(Zod.gte(taxPct, 0, null), 100, null);
        return new SoInput(date, expectedDelivery, partyId, customerRefNo, quotationPresent, quotationId, items, notes,
                potongan, biayaLain, taxPct);
    }

    record GenerateDoItem(String salesOrderItemId, BigDecimal quantity, String warehouseId, String description) {
    }

    record GenerateDoInput(String date, String nomorMobil, String driverName, String recipientName, String notes,
                           boolean allowNegativeStock, String negativeStockReason, List<String> temporaryNoteIds,
                           List<GenerateDoItem> items) {
    }

    /** {@code GenerateDOFromSOSchema}. */
    static GenerateDoInput generateDo(JsonNode raw) {
        ObjectNode o = Zod.object(raw);
        String date = Zod.min(Zod.string(o, "date"), 1, null);
        String nomorMobil = Zod.nullableString(o, "nomorMobil");
        String driverName = Zod.nullableString(o, "driverName");
        String recipientName = Zod.nullableString(o, "recipientName");
        String notes = Zod.nullableString(o, "notes");
        boolean allowNegative = Zod.booleanOr(o, "allowNegativeStock", false);
        String reason = Zod.max(Zod.nullableString(o, "negativeStockReason"), 500);
        List<String> noteIds = Zod.optionalStringArray(o, "temporaryNoteIds");
        List<JsonNode> rawItems = Zod.array(o, "items");
        List<GenerateDoItem> items = new ArrayList<>();
        for (JsonNode el : rawItems) {
            ObjectNode it = Zod.element(el);
            String soItemId = Zod.min(Zod.string(it, "salesOrderItemId"), 1, null);
            BigDecimal quantity = Zod.positive(Zod.coerceNumber(it, "quantity"), null);
            String warehouseId = Zod.nullableString(it, "warehouseId");
            String description = Zod.nullableString(it, "description");
            items.add(new GenerateDoItem(soItemId, quantity, warehouseId, description));
        }
        Zod.minItems(rawItems, 1, null);
        return new GenerateDoInput(date, nomorMobil, driverName, recipientName, notes, allowNegative, reason, noteIds, items);
    }

    // ───────────────────────────── Delivery Order ─────────────────────────────

    /** Baris DO; {@code inventoryItemId}/{@code warehouseId} "" dinormalisasi null (falsy di sumber). */
    record DoItem(String inventoryItemId, String warehouseId, String itemName, BigDecimal quantity, String unit,
                  String description) {
    }

    /** Padanan {@code SubmitDeliveryOrderInput}. */
    record DoInput(String date, String partyId, String nomorMobil, String driverName, String recipientName,
                   String notes, List<DoItem> items, boolean allowNegativeStock, String negativeStockReason,
                   List<String> temporaryNoteIds) {

        DoInput withAllowNegativeStock(boolean allow) {
            return new DoInput(date, partyId, nomorMobil, driverName, recipientName, notes, items, allow,
                    negativeStockReason, temporaryNoteIds);
        }
    }

    /** {@code CreateDeliveryOrderSchema} (+ superRefine alasan stok minus). */
    static DoInput deliveryOrder(JsonNode raw) {
        ObjectNode o = Zod.object(raw);
        String date = Zod.min(Zod.string(o, "date"), 1, "Tanggal wajib diisi.");
        String partyId = Zod.uuid(Zod.string(o, "partyId"), "Customer wajib dipilih.");
        String nomorMobil = Zod.max(Zod.nullableString(o, "nomorMobil"), 50);
        String driverName = Zod.max(Zod.nullableString(o, "driverName"), 200);
        String recipientName = Zod.max(Zod.nullableString(o, "recipientName"), 200);
        String notes = Zod.nullableString(o, "notes");
        List<JsonNode> rawItems = Zod.array(o, "items");
        List<DoItem> items = new ArrayList<>();
        for (JsonNode el : rawItems) {
            ObjectNode it = Zod.element(el);
            String inventoryItemId = Zod.uuid(Zod.nullableString(it, "inventoryItemId"), null);
            String warehouseId = Zod.uuid(Zod.nullableString(it, "warehouseId"), null);
            String itemName = Zod.min(Zod.string(it, "itemName"), 1, "Nama item wajib diisi.");
            BigDecimal quantity = Zod.positive(Zod.coerceNumber(it, "quantity"), "Jumlah harus lebih dari 0.");
            String unit = Zod.min(Zod.string(it, "unit"), 1, "Satuan wajib diisi.");
            String description = Zod.nullableString(it, "description");
            items.add(new DoItem(Zod.truthy(inventoryItemId), Zod.truthy(warehouseId), itemName, quantity, unit, description));
        }
        Zod.minItems(rawItems, 1, "Minimal satu item.");
        boolean allowNegative = Zod.booleanOr(o, "allowNegativeStock", false);
        String reason = Zod.max(Zod.nullableString(o, "negativeStockReason"), 500);
        List<String> noteIds = Zod.optionalStringArray(o, "temporaryNoteIds");
        if (allowNegative && (reason == null || reason.isEmpty() || reason.strip().length() < 10)) {
            throw Zod.fail("Alasan bypass stok minus wajib diisi (minimal 10 karakter).");
        }
        return new DoInput(date, partyId, nomorMobil, driverName, recipientName, notes, items, allowNegative, reason, noteIds);
    }

    record DoFromInvoiceInput(String date, String nomorMobil, String driverName, String recipientName, String notes) {
    }

    /** {@code CreateDOFromInvoiceSchema}. */
    static DoFromInvoiceInput deliveryOrderFromInvoice(JsonNode raw) {
        ObjectNode o = Zod.object(raw);
        String date = Zod.min(Zod.string(o, "date"), 1, "Tanggal wajib diisi.");
        String nomorMobil = Zod.max(Zod.nullableString(o, "nomorMobil"), 50);
        String driverName = Zod.max(Zod.nullableString(o, "driverName"), 200);
        String recipientName = Zod.max(Zod.nullableString(o, "recipientName"), 200);
        String notes = Zod.nullableString(o, "notes");
        return new DoFromInvoiceInput(date, nomorMobil, driverName, recipientName, notes);
    }

    // ───────────────────────────── Sales Return ─────────────────────────────

    record ReturnItem(String salesInvoiceItemId, BigDecimal quantity, boolean restock, String warehouseId) {
    }

    record ReturnInput(String salesInvoiceId, String date, List<ReturnItem> items, String reason, String notes) {
    }

    /** {@code CreateSalesReturnSchema}. */
    static ReturnInput salesReturn(JsonNode raw) {
        ObjectNode o = Zod.object(raw);
        String invoiceId = Zod.min(Zod.string(o, "salesInvoiceId"), 1, "Invoice penjualan wajib dipilih.");
        String date = Zod.min(Zod.string(o, "date"), 1, "Tanggal wajib diisi.");
        List<JsonNode> rawItems = Zod.array(o, "items");
        List<ReturnItem> items = new ArrayList<>();
        for (JsonNode el : rawItems) {
            ObjectNode it = Zod.element(el);
            String lineId = Zod.min(Zod.string(it, "salesInvoiceItemId"), 1, "Baris invoice wajib dipilih.");
            BigDecimal quantity = Zod.positive(Zod.coerceNumber(it, "quantity"), "Qty retur harus > 0.");
            boolean restock = Zod.booleanOr(it, "restock", true);
            String warehouseId = Zod.nullableString(it, "warehouseId");
            items.add(new ReturnItem(lineId, quantity, restock, warehouseId));
        }
        Zod.minItems(rawItems, 1, "Minimal satu baris retur.");
        String reason = Zod.nullableString(o, "reason");
        String notes = Zod.nullableString(o, "notes");
        return new ReturnInput(invoiceId, date, items, reason, notes);
    }

    // ───────────────────────────── Surat Jalan Sementara ─────────────────────────────

    record TdnItem(String inventoryItemId, String itemName, BigDecimal quantity, String unit, String description) {
    }

    record TdnInput(String date, String partyId, String nomorMobil, String driverName, String recipientName,
                    String salesOrderId, String notes, List<TdnItem> items) {
    }

    /** {@code TdnSchema} (routes/temporaryDeliveryNotes.ts). */
    static TdnInput temporaryNote(JsonNode raw) {
        ObjectNode o = Zod.object(raw);
        String date = Zod.min(Zod.string(o, "date"), 1, "Tanggal wajib diisi.");
        String partyId = Zod.uuid(Zod.string(o, "partyId"), "Customer wajib dipilih.");
        String nomorMobil = Zod.max(Zod.nullableString(o, "nomorMobil"), 50);
        String driverName = Zod.max(Zod.nullableString(o, "driverName"), 200);
        String recipientName = Zod.max(Zod.nullableString(o, "recipientName"), 200);
        String salesOrderId = Zod.nullableString(o, "salesOrderId");
        String notes = Zod.nullableString(o, "notes");
        List<JsonNode> rawItems = Zod.array(o, "items");
        List<TdnItem> items = new ArrayList<>();
        for (JsonNode el : rawItems) {
            ObjectNode it = Zod.element(el);
            String inventoryItemId = Zod.min(Zod.string(it, "inventoryItemId"), 1, "Barang wajib dipilih dari master item.");
            String itemName = Zod.optionalString(it, "itemName");
            BigDecimal quantity = Zod.positive(Zod.number(it, "quantity"), "Qty harus lebih dari 0.");
            String unit = Zod.max(Zod.optionalString(it, "unit"), 30);
            String description = Zod.nullableString(it, "description");
            items.add(new TdnItem(inventoryItemId, itemName, quantity, unit, description));
        }
        Zod.minItems(rawItems, 1, "Minimal satu item.");
        return new TdnInput(date, partyId, nomorMobil, driverName, recipientName, salesOrderId, notes, items);
    }

    /** {@code z.object({ deliveryOrderId: z.string().min(1) })} (POST /:id/link). */
    static String linkBody(JsonNode raw) {
        ObjectNode o = Zod.object(raw);
        return Zod.min(Zod.string(o, "deliveryOrderId"), 1, null);
    }
}
