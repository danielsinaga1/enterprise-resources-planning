package id.enterpriseapps.erp.production;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import id.enterpriseapps.erp.production.ProductionRequests.ProductionRunRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Validasi body request produksi — padanan {@code validateBody(Schema, req.body, res)} + Zod 4.
 *
 * <p>Body diterima sebagai {@link JsonNode} (payload draft/antrian approval disimpan apa adanya),
 * dinormalkan untuk field {@code z.coerce.number()} (null → 0 bila tidak nullable, string angka →
 * angka, teks non-angka → NaN = error), lalu diikat ke record DTO dan divalidasi Jakarta
 * Validation. Seperti Zod, yang dilaporkan adalah issue PERTAMA menurut urutan field skema.
 * Kelas ini publik agar modul Approval (resubmit Data Produksi) bisa memvalidasi payload yang sama.
 */
@Component
public class ErpProductionBodyParser {

    static final String NAN = "Invalid input: expected number, received NaN";

    /** Field {@code z.coerce.number()} pada CreateProductionRunSchema (path, nullable). */
    static final List<Coerce> RUN_NUMBERS = List.of(
            new Coerce("inputs[].quantity", false),
            new Coerce("inputs[].batchUnitCost", true),
            new Coerce("outputs[].quantity", false),
            new Coerce("outputs[].unitPrice", true),
            new Coerce("costs[].rate", false),
            new Coerce("hppOverride", true),
            new Coerce("manpowerCount", true),
            new Coerce("goodQty", true),
            new Coerce("rejectQty", true),
            new Coerce("downtimes[].durationMin", false));

    private final Validator validator;

    public ErpProductionBodyParser(ObjectProvider<Validator> validators) {
        Validator v = validators.getIfUnique();
        this.validator = v != null ? v : Validation.buildDefaultValidatorFactory().getValidator();
    }

    /** Spesifikasi koersi angka: path bertitik, {@code []} = setiap elemen array. */
    record Coerce(String path, boolean nullable) {
    }

    private record Issue(List<Integer> key, String message, boolean preBinding) {
    }

    /**
     * Validasi body CreateProductionRunSchema / UpdateProductionRunSchema termasuk superRefine
     * (alasan bypass stok minus, NRV produk sampingan). 400 {@code { error }} pesan pertama.
     */
    public ProductionRunRequest parseProductionRun(JsonNode raw) {
        ProductionRunRequest body = parse(raw, ProductionRunRequest.class, RUN_NUMBERS);
        String refine = productionRunRefinement(body);
        if (refine != null) {
            throw ErpException.business(refine);
        }
        return body;
    }

    /** superRefine CreateProductionRunSchema; null bila lolos. */
    static String productionRunRefinement(ProductionRunRequest b) {
        if (b.negativeStock() && (b.negativeStockReason() == null || b.negativeStockReason().trim().length() < 10)) {
            return "Alasan bypass stok minus wajib diisi (minimal 10 karakter).";
        }
        for (ProductionRequests.OutputLine o : b.outputs()) {
            if (o.byProduct() && (o.unitPrice() == null || o.unitPrice().signum() < 0)) {
                return "NRV produk sampingan wajib diisi (minimal 0).";
            }
        }
        return null;
    }

    /** Ikat + validasi; melempar 400 dengan issue pertama (urutan field skema). */
    public <T> T parse(JsonNode raw, Class<T> type, Collection<Coerce> numbers) {
        return parse(raw, type, numbers, java.util.Map.of());
    }

    /**
     * Seperti {@link #parse(JsonNode, Class, Collection)} untuk skema {@code .partial()}: field tingkat
     * atas yang TIDAK nullable namun dikirim {@code null} ditolak seperti Zod. {@code nonNullable}:
     * nama field → pesan Zod (mis. "Invalid input: expected string, received null").
     */
    public <T> T parse(JsonNode raw, Class<T> type, Collection<Coerce> numbers, java.util.Map<String, String> nonNullable) {
        JsonNode src = raw == null || raw.isNull() || raw.isMissingNode() ? ErpJson.object() : raw;
        if (!src.isObject()) {
            throw ErpException.business("Invalid input: expected object, received " + jsType(src));
        }
        ObjectNode copy = ((ObjectNode) src).deepCopy();
        List<Issue> issues = new ArrayList<>();
        nonNullable.forEach((field, message) -> {
            if (copy.has(field) && copy.get(field).isNull()) {
                issues.add(new Issue(orderKey(type, List.<Object>of(field)), message, true));
            }
        });
        for (Coerce c : numbers) {
            coerce(copy, c.path().split("\\."), 0, new ArrayList<>(), c.nullable(), type, issues);
        }
        T value;
        try {
            value = ErpJson.mapper().treeToValue(copy, type);
        } catch (MismatchedInputException e) {
            throw ErpException.business(mismatchMessage(copy, e));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw ErpException.business("Format data tidak valid.");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        for (ConstraintViolation<T> v : violations) {
            issues.add(new Issue(orderKey(type, v.getPropertyPath()), v.getMessage(), false));
        }
        if (!issues.isEmpty()) {
            Issue first = issues.stream().min(Comparator.comparing(Issue::key, PATH_ORDER)
                    .thenComparing(i -> !i.preBinding())).orElseThrow();
            throw ErpException.business(first.message());
        }
        return value;
    }

    // ───────────────────────────── koersi z.coerce.number ─────────────────────────────

    private void coerce(JsonNode node, String[] segs, int i, List<Object> path, boolean nullable, Class<?> root,
                        List<Issue> issues) {
        if (node == null || !node.isObject()) {
            return;
        }
        String seg = segs[i];
        boolean each = seg.endsWith("[]");
        String name = each ? seg.substring(0, seg.length() - 2) : seg;
        if (!node.has(name)) {
            return;
        }
        JsonNode child = node.get(name);
        List<Object> here = new ArrayList<>(path);
        here.add(name);
        if (each) {
            if (child == null || !child.isArray()) {
                return;
            }
            ArrayNode arr = (ArrayNode) child;
            for (int idx = 0; idx < arr.size(); idx++) {
                List<Object> p = new ArrayList<>(here);
                p.add(idx);
                coerce(arr.get(idx), segs, i + 1, p, nullable, root, issues);
            }
            return;
        }
        if (i < segs.length - 1) {
            coerce(child, segs, i + 1, here, nullable, root, issues);
            return;
        }
        ObjectNode obj = (ObjectNode) node;
        if (child.isNull()) {
            if (!nullable) {
                obj.put(name, BigDecimal.ZERO); // Number(null) = 0
            }
            return;
        }
        BigDecimal n = jsNumber(child);
        if (n == null) {
            obj.putNull(name);
            issues.add(new Issue(orderKey(root, here), NAN, true));
        } else {
            obj.set(name, DecimalNode.valueOf(n));
        }
    }

    /** {@code Number(x)} JS; null = NaN/Infinity. */
    static BigDecimal jsNumber(JsonNode v) {
        if (v.isNumber()) {
            return v.decimalValue();
        }
        if (v.isBoolean()) {
            return v.booleanValue() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (v.isTextual()) {
            String s = v.asText().trim();
            if (s.isEmpty()) {
                return BigDecimal.ZERO;
            }
            if (s.matches("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")) {
                return new BigDecimal(s.startsWith("+") ? s.substring(1) : s);
            }
            if (s.matches("0[xX][0-9a-fA-F]+")) {
                return new BigDecimal(new java.math.BigInteger(s.substring(2), 16));
            }
            return null;
        }
        return null;
    }

    // ───────────────────────────── pesan & urutan ─────────────────────────────

    /** Pesan Zod 4 untuk tipe JSON yang salah (best effort); fallback "Format data tidak valid.". */
    private static String mismatchMessage(JsonNode root, MismatchedInputException e) {
        JsonNode cur = root;
        for (var ref : e.getPath()) {
            if (cur == null) {
                break;
            }
            cur = ref.getFieldName() != null ? cur.get(ref.getFieldName())
                    : ref.getIndex() >= 0 ? cur.get(ref.getIndex()) : null;
        }
        Class<?> target = e.getTargetType();
        String expected = target == null ? null
                : List.class.isAssignableFrom(target) || target.isArray() ? "array"
                : target == String.class ? "string"
                : Number.class.isAssignableFrom(target) ? "number"
                : target == Boolean.class || target == boolean.class ? "boolean"
                : target.isRecord() ? "object" : null;
        if (expected == null || cur == null) {
            return "Format data tidak valid.";
        }
        return "Invalid input: expected " + expected + ", received " + jsType(cur);
    }

    /** Nama tipe gaya Zod untuk pesan invalid_type. */
    static String jsType(JsonNode n) {
        if (n == null || n.isMissingNode()) {
            return "undefined";
        }
        if (n.isNull()) {
            return "null";
        }
        if (n.isNumber()) {
            return "number";
        }
        if (n.isBoolean()) {
            return "boolean";
        }
        if (n.isArray()) {
            return "array";
        }
        if (n.isObject()) {
            return "object";
        }
        return "string";
    }

    private static final Comparator<List<Integer>> PATH_ORDER = (a, b) -> {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            int c = Integer.compare(a.get(i), b.get(i));
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(a.size(), b.size());
    };

    /** Kunci urutan untuk path violation Jakarta. */
    private static List<Integer> orderKey(Class<?> root, Path path) {
        List<Object> segs = new ArrayList<>();
        for (Path.Node node : path) {
            if (node.isInIterable() && node.getIndex() != null) {
                segs.add(node.getIndex());
            }
            if (node.getName() != null) {
                segs.add(node.getName());
            }
        }
        return orderKey(root, segs);
    }

    /** Kunci urutan: indeks komponen record per nama + indeks elemen list. */
    private static List<Integer> orderKey(Class<?> root, List<Object> segs) {
        List<Integer> key = new ArrayList<>();
        Class<?> cls = root;
        for (Object seg : segs) {
            if (seg instanceof Integer idx) {
                key.add(idx);
                continue;
            }
            String name = (String) seg;
            if (cls == null || !cls.isRecord()) {
                key.add(Integer.MAX_VALUE);
                continue;
            }
            int idx = Integer.MAX_VALUE;
            Class<?> next = null;
            RecordComponent[] comps = cls.getRecordComponents();
            for (int i = 0; i < comps.length; i++) {
                if (comps[i].getName().equals(name)) {
                    idx = i;
                    next = elementType(comps[i].getGenericType());
                    break;
                }
            }
            key.add(idx);
            cls = next;
        }
        return key;
    }

    private static Class<?> elementType(Type t) {
        if (t instanceof ParameterizedType p && p.getActualTypeArguments().length == 1) {
            Type arg = p.getActualTypeArguments()[0];
            if (arg instanceof Class<?> c) {
                return c;
            }
            if (arg instanceof ParameterizedType pp && pp.getRawType() instanceof Class<?> c) {
                return c;
            }
            return null;
        }
        return t instanceof Class<?> c ? c : null;
    }
}
