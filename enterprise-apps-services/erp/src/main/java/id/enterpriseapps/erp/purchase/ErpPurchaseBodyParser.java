package id.enterpriseapps.erp.purchase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.core.ErpJson;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Validasi body request pembelian — padanan {@code validateBody(Schema, req.body, res)}.
 *
 * <p>Body diterima sebagai {@link JsonNode} (dibutuhkan apa adanya untuk payload antrian approval),
 * dikonversi ke record DTO lalu divalidasi Jakarta Validation. Seperti Zod, yang dikembalikan
 * adalah error PERTAMA menurut urutan field skema (bukan urutan acak Set violation) → 400 {@code { error }}.
 */
@Component
public class ErpPurchaseBodyParser {

    private final Validator validator;

    public ErpPurchaseBodyParser(ObjectProvider<Validator> validators) {
        Validator v = validators.getIfUnique();
        this.validator = v != null ? v : Validation.buildDefaultValidatorFactory().getValidator();
    }

    public <T> T parse(JsonNode raw, Class<T> type) {
        JsonNode src = raw == null || raw.isNull() || raw.isMissingNode() ? ErpJson.object() : raw;
        if (!src.isObject()) {
            throw ErpException.business("Invalid input: expected object, received " + jsType(src));
        }
        T value;
        try {
            value = ErpJson.mapper().treeToValue(src, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw ErpException.business("Format data tidak valid.");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            ConstraintViolation<T> first = violations.stream()
                    .min(Comparator.comparing(v -> orderKey(type, v.getPropertyPath()), PATH_ORDER))
                    .orElseThrow();
            throw ErpException.business(first.getMessage());
        }
        return value;
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

    /** Kunci urutan: indeks komponen record per segmen path (+ indeks elemen list). */
    private static List<Integer> orderKey(Class<?> root, Path path) {
        List<Integer> key = new ArrayList<>();
        Class<?> cls = root;
        for (Path.Node node : path) {
            if (node.isInIterable() && node.getIndex() != null) {
                key.add(node.getIndex());
            }
            if (node.getName() == null || cls == null || !cls.isRecord()) {
                continue;
            }
            int idx = Integer.MAX_VALUE;
            Class<?> next = null;
            RecordComponent[] comps = cls.getRecordComponents();
            for (int i = 0; i < comps.length; i++) {
                if (comps[i].getName().equals(node.getName())) {
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
