package id.enterpriseapps.erp.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.core.approval.ErpApprovalGateService;
import id.enterpriseapps.erp.entity.ApprovalRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tampilan "sebelum vs sesudah" untuk pengajuan edit dokumen yang sudah ada — port bagian
 * perbandingan {@code services/approvalEditRequests.ts} ({@code buildApprovalComparison},
 * {@code buildProductionEditComparison}, {@code buildInvoiceReviseComparison}).
 *
 * <p>Murni dari payload tersimpan: body asli (sesudah) + snapshot {@code _before} yang ditulis modul
 * domain saat antrian dibuat. Aritmetika memakai number JS (double) karena hasilnya hanya tampilan.
 */
final class ApprovalComparison {

    private static final String PRODUCTION_EDIT = "ProductionRunEdit";

    private ApprovalComparison() {
    }

    /** Dispatcher untuk endpoint detail; null bila bukan tipe edit / snapshot hilang. */
    static ObjectNode build(ApprovalRequest request) {
        String type = request.getDocumentType();
        if (!ErpApprovalGateService.isEditDocumentType(type)) {
            return null;
        }
        JsonNode[] split = ErpApprovalGateService.splitStoredPayload(request.getPayload());
        JsonNode body = split[0];
        JsonNode before = split[1];
        if (!AdminJs.truthy(before)) {
            return null;
        }
        ObjectNode cmp = PRODUCTION_EDIT.equals(type) ? production(before, body) : invoice(before, body);
        cmp.put("targetId", request.getDocumentId());
        if (request.getDocumentNumber() != null) {
            cmp.put("targetNumber", request.getDocumentNumber());
        }
        return cmp;
    }

    // ───────────────────────── Edit Data Produksi ─────────────────────────

    private record Line(String itemId, double quantity, boolean byProduct) {
    }

    /** {@code num(v)}: null/undefined → 0, selain itu {@code Number(v)}. */
    private static double num(JsonNode v) {
        return v == null || v.isMissingNode() || v.isNull() ? 0 : AdminJs.number(v);
    }

    private static List<Line> beforeLines(JsonNode lines) {
        List<Line> out = new ArrayList<>();
        for (JsonNode l : ErpJson.elements(lines)) {
            out.add(new Line(AdminJs.string(l.get("itemId")), AdminJs.number(l.get("quantity")),
                    AdminJs.truthy(l.get("byProduct"))));
        }
        return out;
    }

    private static double sum(List<Line> lines, boolean onlyMain) {
        double s = 0;
        for (Line l : lines) {
            if (!onlyMain || !l.byProduct()) {
                s += l.quantity();
            }
        }
        return s;
    }

    static ObjectNode production(JsonNode before, JsonNode body) {
        JsonNode names = before.get("itemNames");
        List<Line> bInputs = beforeLines(before.get("inputs"));
        List<Line> bOutputs = beforeLines(before.get("outputs"));
        List<Line> aInputs = new ArrayList<>();
        for (JsonNode i : ErpJson.elements(body.get("inputs"))) {
            aInputs.add(new Line(AdminJs.string(i.get("itemId")), num(i.get("quantity")), false));
        }
        List<Line> aOutputs = new ArrayList<>();
        for (JsonNode o : ErpJson.elements(body.get("outputs"))) {
            aOutputs.add(new Line(AdminJs.string(o.get("itemId")), num(o.get("quantity")), AdminJs.truthy(o.get("isByProduct"))));
        }

        double bIn = AdminJs.round(sum(bInputs, false), 3);
        double aIn = AdminJs.round(sum(aInputs, false), 3);
        double bMain = AdminJs.round(sum(bOutputs, true), 3);
        double aMain = AdminJs.round(sum(aOutputs, true), 3);
        double bOut = AdminJs.round(sum(bOutputs, false), 3);
        double aOut = AdminJs.round(sum(aOutputs, false), 3);
        Double bYield = bIn > 0 ? AdminJs.round((bMain / bIn) * 100, 2) : null;
        Double aYield = aIn > 0 ? AdminJs.round((aMain / aIn) * 100, 2) : null;
        JsonNode dateNode = body.get("date");
        String aDate = dateNode != null && dateNode.isTextual()
                ? (dateNode.textValue().length() > 10 ? dateNode.textValue().substring(0, 10) : dateNode.textValue()) : "";
        JsonNode bDate = before.get("date");
        boolean dateChanged = !(bDate != null && bDate.isTextual() && bDate.textValue().equals(aDate));

        ArrayNode summary = ErpJson.arrayNode();
        summary.add(row("Tanggal produksi", bDate, ErpJson.tree(aDate), "text", dateChanged, null));
        summary.add(row("Total input", number(bIn), number(aIn), "number", bIn != aIn, null));
        summary.add(row("Total output (termasuk sampingan)", number(bOut), number(aOut), "number", bOut != aOut, null));
        summary.add(row("Yield produk utama", number(bYield), number(aYield), "percent", !sameNumber(bYield, aYield), null));
        summary.add(row("HPP terhitung (per kg produk utama)", before.get("computedHpp"), ErpJson.tree(null), "currency",
                false, "Dihitung ulang oleh sistem saat disetujui."));

        ObjectNode out = ErpJson.object();
        out.put("title", "Edit Data Produksi");
        out.set("targetNumber", present(before.get("runNumber")));
        out.put("targetType", "ProductionRun");
        out.putNull("targetId");
        out.put("amountLabel", "Total input (baru)");
        ArrayNode sections = ErpJson.arrayNode();
        sections.add(section("Ringkasan", summary));
        sections.add(section("Bahan baku (input)", diffRows(bInputs, aInputs, names, false)));
        sections.add(section("Hasil (output)", diffRows(bOutputs, aOutputs, names, true)));
        out.set("sections", sections);
        return out;
    }

    private static final class Acc {
        final String id;
        final boolean bp;
        double b;
        double a;

        Acc(String id, boolean bp) {
            this.id = id;
            this.bp = bp;
        }
    }

    private static ArrayNode diffRows(List<Line> before, List<Line> after, JsonNode names, boolean suffixByProduct) {
        Map<String, Acc> map = new LinkedHashMap<>();
        for (Line l : before) {
            map.computeIfAbsent(l.itemId() + "|" + (l.byProduct() ? 1 : 0), k -> new Acc(l.itemId(), l.byProduct())).b += l.quantity();
        }
        for (Line l : after) {
            map.computeIfAbsent(l.itemId() + "|" + (l.byProduct() ? 1 : 0), k -> new Acc(l.itemId(), l.byProduct())).a += l.quantity();
        }
        ArrayNode rows = ErpJson.arrayNode();
        for (Acc e : map.values()) {
            JsonNode name = names == null ? null : names.get(e.id);
            String label = (name == null || name.isNull() ? e.id : AdminJs.string(name))
                    + (suffixByProduct && e.bp ? " (produk sampingan)" : "");
            double b = AdminJs.round(e.b, 3);
            double a = AdminJs.round(e.a, 3);
            rows.add(row(label, number(b), number(a), "number", b != a, null));
        }
        return rows;
    }

    // ───────────────────────── Revisi Harga Invoice ─────────────────────────

    private record ReviseLine(String itemName, double quantity, String unit, double oldRate, double newRate, double delta) {
    }

    static ObjectNode invoice(JsonNode before, JsonNode body) {
        Map<String, JsonNode> byId = new HashMap<>();
        for (JsonNode l : ErpJson.elements(before.get("lines"))) {
            JsonNode id = l.get("itemId");
            byId.put(id == null || id.isNull() ? null : AdminJs.string(id), l);
        }
        List<ReviseLine> lines = new ArrayList<>();
        double deltaSum = 0;
        for (JsonNode l : ErpJson.elements(body.get("lines"))) {
            JsonNode old = byId.get(AdminJs.string(l.get("itemId")));
            double newRate = num(l.get("newRate"));
            String itemName = old != null && hasValue(old.get("itemName")) ? AdminJs.string(old.get("itemName")) : "Baris tidak dikenal";
            double quantity = old != null && hasValue(old.get("quantity")) ? AdminJs.number(old.get("quantity")) : 0;
            String unit = old != null && hasValue(old.get("unit")) ? AdminJs.string(old.get("unit")) : "";
            double oldRate = old != null && hasValue(old.get("rate")) ? AdminJs.number(old.get("rate")) : 0;
            double delta = AdminJs.round((newRate - oldRate) * quantity, 2);
            lines.add(new ReviseLine(itemName, quantity, unit, oldRate, newRate, delta));
            deltaSum += delta;
        }
        double totalDelta = AdminJs.round(deltaSum, 2);
        double newTotal = AdminJs.round(AdminJs.number(before.get("grandTotal")) + totalDelta, 2);

        ArrayNode rateRows = ErpJson.arrayNode();
        ArrayNode valueRows = ErpJson.arrayNode();
        for (ReviseLine l : lines) {
            rateRows.add(row(l.itemName() + " · harga per " + (l.unit().isEmpty() ? "unit" : l.unit()),
                    number(l.oldRate()), number(l.newRate()), "currency", l.oldRate() != l.newRate(), null));
            valueRows.add(row((l.itemName() + " · nilai (" + AdminJs.idNumber(l.quantity()) + " " + l.unit() + ")").strip(),
                    number(AdminJs.round(l.oldRate() * l.quantity(), 2)), number(AdminJs.round(l.newRate() * l.quantity(), 2)),
                    "currency", l.oldRate() != l.newRate(), null));
        }
        JsonNode reason = body.get("reason");
        String reasonText = reason == null || reason.isMissingNode() || reason.isNull() ? "" : AdminJs.string(reason);

        ArrayNode summary = ErpJson.arrayNode();
        summary.add(row("Total invoice", before.get("grandTotal"), number(newTotal), "currency", totalDelta != 0,
                "Estimasi dari selisih harga × qty; pajak/potongan dihitung sistem."));
        summary.add(row("Selisih harga", number(0), number(totalDelta), "currency", totalDelta != 0, null));
        summary.add(row("Alasan revisi", ErpJson.tree(null), ErpJson.tree(reasonText), "text", false, null));

        ObjectNode out = ErpJson.object();
        out.put("title", "Revisi Harga Invoice Pembelian");
        out.set("targetNumber", present(before.get("invoiceNumber")));
        out.put("targetType", "PurchaseInvoice");
        out.putNull("targetId");
        out.put("amountLabel", "Total invoice (baru)");
        ArrayNode sections = ErpJson.arrayNode();
        sections.add(section("Ringkasan", summary));
        sections.add(section("Harga per baris", rateRows));
        sections.add(section("Nilai per baris", valueRows));
        out.set("sections", sections);
        return out;
    }

    // ───────────────────────── helper ─────────────────────────

    private static boolean hasValue(JsonNode v) {
        return v != null && !v.isMissingNode() && !v.isNull();
    }

    private static JsonNode number(double v) {
        return ErpJson.tree(AdminJs.json(v));
    }

    private static JsonNode number(Double v) {
        return v == null ? ErpJson.tree(null) : number(v.doubleValue());
    }

    /** {@code a !== b} untuk number|null (NaN tidak pernah sama). */
    private static boolean sameNumber(Double a, Double b) {
        if (a == null || b == null) {
            return Objects.equals(a, b);
        }
        return a.doubleValue() == b.doubleValue();
    }

    /** Nilai apa adanya; undefined (field tidak ada) → null di objek perbandingan. */
    private static JsonNode present(JsonNode v) {
        return v == null || v.isMissingNode() ? ErpJson.tree(null) : v;
    }

    /** Satu baris; field {@code before} yang undefined dihilangkan seperti JSON.stringify. */
    private static ObjectNode row(String label, JsonNode before, JsonNode after, String kind, boolean changed, String note) {
        ObjectNode r = ErpJson.object();
        r.put("label", label);
        if (before != null && !before.isMissingNode()) {
            r.set("before", before);
        }
        r.set("after", after);
        r.put("kind", kind);
        r.put("changed", changed);
        if (note != null) {
            r.put("note", note);
        }
        return r;
    }

    private static ObjectNode section(String title, ArrayNode rows) {
        ObjectNode s = ErpJson.object();
        s.put("title", title);
        s.set("rows", rows);
        return s;
    }
}
