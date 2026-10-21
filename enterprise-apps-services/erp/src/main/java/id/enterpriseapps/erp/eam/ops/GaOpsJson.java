package id.enterpriseapps.erp.eam.ops;

import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.entity.GaAsset;
import id.enterpriseapps.erp.entity.GaInventoryItem;
import id.enterpriseapps.erp.entity.GaInventoryMovement;
import id.enterpriseapps.erp.entity.GaLocation;
import id.enterpriseapps.erp.entity.GaMaintenanceSchedule;
import id.enterpriseapps.erp.entity.GaMaterialRequest;
import id.enterpriseapps.erp.entity.GaMaterialRequestItem;
import id.enterpriseapps.erp.entity.GaStockByLocation;
import id.enterpriseapps.erp.entity.GaWorkOrder;
import id.enterpriseapps.erp.entity.GaWorkOrderPart;
import id.enterpriseapps.erp.entity.GaWorkOrderPhoto;
import org.hibernate.Hibernate;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pembentuk JSON & utilitas kecil bersama paket GA ops.
 *
 * <p>Prisma {@code select} membentuk objek parsial → {@link LinkedHashMap} eksplisit. Untuk
 * {@code { ...entity }} dipakai {@link #scalars(Object)}: hanya kolom skalar (relasi yang kebetulan
 * sudah dimuat di persistence context dibuang) supaya bentuk respons tidak bergantung urutan akses.
 */
final class GaOpsJson {

    private GaOpsJson() {
    }

    /** Nama field relasi per entity (dibuang dari {@link #scalars}). */
    private static final Map<Class<?>, List<String>> RELATIONS = Map.ofEntries(
            Map.entry(GaWorkOrder.class, List.of("asset", "schedule", "photos", "parts", "materialRequests",
                    "vehicleServiceLog", "vehicleServiceReq", "calibrationLog")),
            Map.entry(GaMaterialRequest.class, List.of("workOrder", "asset", "items", "journalEntry",
                    "purchaseInvoice", "glAccount", "creditAccount")),
            Map.entry(GaMaterialRequestItem.class, List.of("materialRequest", "inventoryItem", "receivedIntoLocation")),
            Map.entry(GaMaintenanceSchedule.class, List.of("asset", "workOrders")),
            Map.entry(GaInventoryItem.class, List.of("category", "stocks", "movements", "workOrderParts",
                    "materialRequestItems")),
            Map.entry(GaStockByLocation.class, List.of("item", "location")),
            Map.entry(GaInventoryMovement.class, List.of("item", "fromLocation", "toLocation", "workOrderPart")),
            Map.entry(GaWorkOrderPart.class, List.of("workOrder", "inventoryItem", "fromLocation", "movement")),
            Map.entry(GaWorkOrderPhoto.class, List.of("workOrder")));

    /** Kolom skalar entity sebagai objek JSON (padanan baris Prisma tanpa include). */
    static ObjectNode scalars(Object entity) {
        ObjectNode o = ErpJson.obj(entity);
        List<String> rel = RELATIONS.get(Hibernate.getClass(entity));
        if (rel != null) {
            rel.forEach(o::remove);
        }
        return o;
    }

    /** {@code Decimal.toNumber()}: angka polos tanpa nol di belakang koma (null tetap null). */
    static BigDecimal num(BigDecimal v) {
        if (v == null) {
            return null;
        }
        if (v.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    /** {@code String(decimal)} ala decimal.js (tanpa notasi eksponen untuk nilai wajar). */
    static String numText(BigDecimal v) {
        return num(v == null ? BigDecimal.ZERO : v).toPlainString();
    }

    static Map<String, Object> map(Object... kv) {
        return ErpJson.map(kv);
    }

    /** {@code asset: { select: { id, code, name } }} (null bila tidak ada). */
    static Map<String, Object> assetBrief(GaAsset a) {
        return a == null ? null : map("id", a.getId(), "code", a.getCode(), "name", a.getName());
    }

    /** {@code asset: { select: { id, code, name, picUserId } }}. */
    static Map<String, Object> assetWithPic(GaAsset a) {
        return a == null ? null : map("id", a.getId(), "code", a.getCode(), "name", a.getName(),
                "picUserId", a.getPicUserId());
    }

    /** {@code location: { select: { code, name } }}. */
    static Map<String, Object> locCodeName(GaLocation l) {
        return l == null ? null : map("code", l.getCode(), "name", l.getName());
    }

    /** {@code location: { select: { id, code, name } }}. */
    static Map<String, Object> locFull(GaLocation l) {
        return l == null ? null : map("id", l.getId(), "code", l.getCode(), "name", l.getName());
    }

    /** {@code item: { select: { sku, name, uom } }}. */
    static Map<String, Object> itemBrief(GaInventoryItem i) {
        return i == null ? null : map("sku", i.getSku(), "name", i.getName(), "uom", i.getUom());
    }

    /** {@code parseInt(String(v), 10)} JavaScript: awalan angka, NaN → null. */
    static Integer jsParseInt(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        int i = 0;
        boolean neg = false;
        if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
            neg = s.charAt(i) == '-';
            i++;
        }
        int start = i;
        while (i < s.length() && Character.isDigit(s.charAt(i)) && s.charAt(i) < 128) {
            i++;
        }
        if (i == start) {
            return null;
        }
        String digits = s.substring(start, i);
        try {
            long v = Long.parseLong(digits.length() > 12 ? digits.substring(0, 12) : digits);
            v = neg ? -v : v;
            return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final Collator COLLATOR = collator();

    private static Collator collator() {
        Collator c = Collator.getInstance(Locale.forLanguageTag("id"));
        c.setStrength(Collator.PRIMARY);
        return c;
    }

    /**
     * Perbandingan {@code Intl.Collator('id', { sensitivity: 'base', numeric: true })}: tanpa beda
     * huruf besar/aksen, potongan angka dibandingkan sebagai bilangan.
     */
    static int naturalCompare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            boolean da = Character.isDigit(a.charAt(i));
            boolean db = Character.isDigit(b.charAt(j));
            int ei = chunkEnd(a, i, da);
            int ej = chunkEnd(b, j, db);
            String ca = a.substring(i, ei);
            String cb = b.substring(j, ej);
            int cmp;
            if (da && db) {
                cmp = new java.math.BigInteger(ca).compareTo(new java.math.BigInteger(cb));
            } else {
                cmp = COLLATOR.compare(ca, cb);
            }
            if (cmp != 0) {
                return cmp;
            }
            i = ei;
            j = ej;
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    private static int chunkEnd(String s, int from, boolean digits) {
        int k = from;
        while (k < s.length() && Character.isDigit(s.charAt(k)) == digits) {
            k++;
        }
        return k;
    }
}
