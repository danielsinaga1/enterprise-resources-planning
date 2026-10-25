package id.neravoxa.erp.eam.vehicle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * Tiruan kecil Zod 4 untuk skema body rute kendaraan ({@code routes/ga/vehicle.ts}).
 *
 * <p>{@code validateBody} ENTARIS mengembalikan {@code issues[0].message}; Zod memeriksa field sesuai
 * urutan skema, jadi pemanggilan method field BERURUTAN (error pertama menang) menghasilkan pesan yang
 * sama. Pesan bawaan Zod (locale en) disalin persis.
 *
 * <p>Mode {@code partial} = {@code Schema.partial()}: field wajib menjadi opsional (absen boleh), tetapi
 * {@code null} tetap ditolak kecuali field-nya {@code .nullable()}. Sesuai Zod 4, nilai {@code .default()}
 * TETAP diterapkan pada field yang absen walau skemanya partial.
 */
final class VehicleZod {

    /** Regex {@code z.string().uuid()} Zod 4 (RFC 9562 + nil/max UUID). */
    private static final Pattern UUID = Pattern.compile(
            "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
                    + "|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)$");

    private final ObjectNode body;
    private final boolean partial;

    private VehicleZod(ObjectNode body, boolean partial) {
        this.body = body;
        this.partial = partial;
    }

    /** Body JSON wajib objek (Express 5: tanpa body = undefined). */
    static VehicleZod of(JsonNode raw, boolean partial) {
        if (raw == null || raw.isMissingNode()) {
            throw ErpException.business("Invalid input: expected object, received undefined");
        }
        if (!raw.isObject()) {
            throw ErpException.business(invalidType("object", raw));
        }
        return new VehicleZod((ObjectNode) raw, partial);
    }

    /** Field dikirim (termasuk {@code null}) — padanan {@code input.x !== undefined}. */
    boolean has(String key) {
        return body.has(key);
    }

    static String typeName(JsonNode n) {
        if (n == null || n.isMissingNode()) {
            return "undefined";
        }
        if (n.isNull()) {
            return "null";
        }
        if (n.isTextual()) {
            return "string";
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
        return "object";
    }

    static String invalidType(String expected, JsonNode received) {
        return "Invalid input: expected " + expected + ", received " + typeName(received);
    }

    static String enumMessage(List<String> options) {
        StringBuilder sb = new StringBuilder("Invalid option: expected one of ");
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append('"').append(options.get(i)).append('"');
        }
        return sb.toString();
    }

    private static ErpException fail(String message) {
        return ErpException.business(message);
    }

    /**
     * Nilai mentah field; null bila absen (opsional/partial) atau null (nullable). Melempar issue tipe
     * bila absen padahal wajib, atau null padahal tidak nullable.
     */
    private JsonNode raw(String key, String expected, boolean optional, boolean nullable) {
        JsonNode v = body.get(key);
        if (v == null) {
            if (optional || partial) {
                return null;
            }
            throw fail(invalidType(expected, null));
        }
        if (v.isNull()) {
            if (nullable) {
                return null;
            }
            throw fail(invalidType(expected, v));
        }
        return v;
    }

    /** {@code z.string()[.min(min, minMsg)][.max(max)]} + optional/nullable. */
    String string(String key, int min, String minMsg, Integer max, boolean optional, boolean nullable) {
        JsonNode v = raw(key, "string", optional, nullable);
        if (v == null) {
            return null;
        }
        if (!v.isTextual()) {
            throw fail(invalidType("string", v));
        }
        String s = v.textValue();
        if (s.length() < min) {
            throw fail(minMsg != null ? minMsg : "Too small: expected string to have >=" + min + " characters");
        }
        if (max != null && s.length() > max) {
            throw fail("Too big: expected string to have <=" + max + " characters");
        }
        return s;
    }

    /** {@code z.string().max(max).optional().nullable()}. */
    String optString(String key, Integer max) {
        return string(key, 0, null, max, true, true);
    }

    /** {@code z.string().uuid().optional().nullable()}. */
    String optUuid(String key) {
        String s = optString(key, null);
        if (s != null && !UUID.matcher(s).matches()) {
            throw fail("Invalid UUID");
        }
        return s;
    }

    /** Angka JSON (z.number() menolak string). */
    private BigDecimal numeric(String key, boolean optional, boolean nullable) {
        JsonNode v = raw(key, "number", optional, nullable);
        if (v == null) {
            return null;
        }
        if (!v.isNumber()) {
            throw fail(invalidType("number", v));
        }
        return v.decimalValue();
    }

    /**
     * {@code z.number().int()[.min(min)][.max(max)]} + optional/nullable. {@code min} null = tanpa batas
     * bawah; {@code nonnegative()} = min 0.
     */
    Integer integer(String key, Long min, Long max, boolean optional, boolean nullable) {
        BigDecimal d = numeric(key, optional, nullable);
        if (d == null) {
            return null;
        }
        BigDecimal stripped = d.stripTrailingZeros();
        if (stripped.scale() > 0) {
            throw fail("Invalid input: expected int, received number");
        }
        if (min != null && d.compareTo(BigDecimal.valueOf(min)) < 0) {
            throw fail("Too small: expected number to be >=" + min);
        }
        if (max != null && d.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw fail("Too big: expected number to be <=" + max);
        }
        if (d.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            // Di luar rentang kolom INTEGER (Prisma gagal menulis) — tolak di sini dengan pesan Zod int32.
            throw fail("Too big: expected number to be <=" + Integer.MAX_VALUE);
        }
        if (d.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0) {
            throw fail("Too small: expected number to be >=" + Integer.MIN_VALUE);
        }
        return d.intValueExact();
    }

    /** {@code z.number().nonnegative()} + optional/nullable. */
    BigDecimal nonNegative(String key, boolean optional, boolean nullable) {
        BigDecimal d = numeric(key, optional, nullable);
        if (d != null && d.signum() < 0) {
            throw fail("Too small: expected number to be >=0");
        }
        return d;
    }

    /** {@code z.number().positive(msg)} (wajib). */
    BigDecimal positive(String key, String message) {
        BigDecimal d = numeric(key, false, false);
        if (d != null && d.signum() <= 0) {
            throw fail(message);
        }
        return d;
    }

    /** {@code z.boolean().optional()}. */
    Boolean optBoolean(String key) {
        JsonNode v = raw(key, "boolean", true, false);
        if (v == null) {
            return null;
        }
        if (!v.isBoolean()) {
            throw fail(invalidType("boolean", v));
        }
        return v.booleanValue();
    }

    /**
     * {@code z.enum(options)[.default(dflt)]} + optional/nullable. Absen → default (juga di mode partial,
     * semantik Zod 4); nilai di luar daftar (termasuk null pada enum non-nullable) → invalid_value.
     */
    String enumValue(String key, List<String> options, String dflt, boolean optional, boolean nullable) {
        JsonNode v = body.get(key);
        if (v == null) {
            if (dflt != null) {
                return dflt;
            }
            if (optional || partial) {
                return null;
            }
            throw fail(enumMessage(options));
        }
        if (v.isNull() && nullable) {
            return null;
        }
        if (v.isTextual() && options.contains(v.textValue())) {
            return v.textValue();
        }
        throw fail(enumMessage(options));
    }

    /** {@code z.record(z.string(), z.enum(values))} (wajib). Urutan kunci dipertahankan. */
    Map<String, String> enumRecord(String key, List<String> values) {
        JsonNode v = body.get(key);
        if (v == null || !v.isObject()) {
            if (v == null && partial) {
                return null;
            }
            throw fail(invalidType("record", v));
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (var it = v.fields(); it.hasNext(); ) {
            Entry<String, JsonNode> e = it.next();
            JsonNode val = e.getValue();
            if (!val.isTextual() || !values.contains(val.textValue())) {
                throw fail(enumMessage(values));
            }
            out.put(e.getKey(), val.textValue());
        }
        return out;
    }
}
