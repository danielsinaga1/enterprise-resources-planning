package id.enterpriseapps.erp.core;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serialisasi JSON modul ERP.
 *
 * <p>Mapper ini sengaja terpisah dari ObjectMapper global Spring agar konfigurasinya tidak
 * mengubah perilaku modul lain:
 * <ul>
 *   <li>{@link Hibernate6Module}: proxy/koleksi lazy yang belum di-fetch tidak memicu query
 *       dan — lewat {@code LazyNotLoadedFilter} pada field relasi — tidak ditulis sama sekali,
 *       meniru {@code include} Prisma. Relasi yang di-JOIN FETCH / diinisialisasi ikut tertulis.</li>
 *   <li>{@link Instant} ditulis {@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'} persis seperti Prisma/JS
 *       {@code Date#toJSON()}; {@code BigDecimal} ditulis polos (tanpa notasi eksponen).</li>
 * </ul>
 * Controller ERP cukup mengembalikan entity/Map/record apa pun; {@code ErpResponseAdvice}
 * mengubahnya lewat {@link #tree(Object)} lalu membungkusnya ke {@code Response}.
 */
public final class ErpJson {

    private static final DateTimeFormatter JS_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static final ObjectMapper MAPPER = build();

    private ErpJson() {
    }

    private static ObjectMapper build() {
        ObjectMapper m = new ObjectMapper();
        Hibernate6Module hibernate = new Hibernate6Module();
        hibernate.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        hibernate.disable(Hibernate6Module.Feature.USE_TRANSIENT_ANNOTATION);
        hibernate.disable(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS);
        m.registerModule(hibernate);
        m.registerModule(new JavaTimeModule());
        SimpleModule js = new SimpleModule("erp-js-compat");
        js.addSerializer(Instant.class, new StdSerializer<>(Instant.class) {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(JS_ISO.format(value));
            }
        });
        m.registerModule(js);
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        m.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        m.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        m.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
        // Pohon JSON menyimpan BigDecimal apa adanya (tanpa normalisasi ke 1E+9), karena pohon ini
        // kemudian ditulis ObjectMapper global Spring yang tidak memakai WRITE_BIGDECIMAL_AS_PLAIN.
        m.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        return m;
    }

    /** Mapper ERP (thread-safe). */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** Ubah objek apa pun (entity, Map, record, List) ke pohon JSON — semantik include Prisma. */
    public static JsonNode tree(Object value) {
        if (value == null) {
            return NullNode.getInstance();
        }
        if (value instanceof JsonNode node) {
            return node;
        }
        return MAPPER.valueToTree(value);
    }

    /**
     * Objek JSON dari entity/Map, siap ditambah field turunan — padanan {@code { ...entity, extra }}.
     * Melempar IllegalArgumentException bila nilainya bukan objek.
     */
    public static ObjectNode obj(Object value) {
        JsonNode node = tree(value);
        if (node instanceof ObjectNode o) {
            return o;
        }
        throw new IllegalArgumentException("Bukan objek JSON: " + (value == null ? "null" : value.getClass()));
    }

    /** {@code { ...base, ...extra }} — nilai extra dikonversi lewat {@link #tree(Object)}. */
    public static ObjectNode merge(Object base, Map<String, ?> extra) {
        ObjectNode o = obj(base);
        if (extra != null) {
            extra.forEach((k, v) -> o.set(k, tree(v)));
        }
        return o;
    }

    /** Daftar objek JSON — untuk {@code list.map(x => ({ ...x, extra }))}. */
    public static ArrayNode array(Collection<?> values) {
        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        if (values != null) {
            values.forEach(v -> arr.add(tree(v)));
        }
        return arr;
    }

    public static ObjectNode object() {
        return JsonNodeFactory.instance.objectNode();
    }

    public static ArrayNode arrayNode() {
        return JsonNodeFactory.instance.arrayNode();
    }

    /** Map berurutan dari pasangan key/value: {@code ErpJson.map("a", 1, "b", 2)}. */
    public static Map<String, Object> map(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("ErpJson.map butuh pasangan key/value");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    public static <T> T convert(Object value, Class<T> type) {
        return MAPPER.convertValue(value, type);
    }

    public static <T> T convert(Object value, com.fasterxml.jackson.core.type.TypeReference<T> type) {
        return MAPPER.convertValue(value, type);
    }

    public static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return NullNode.getInstance();
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON tidak valid", e);
        }
    }

    public static String stringify(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Gagal serialisasi JSON", e);
        }
    }

    /** Teks atau null dari node (null/missing → null). */
    public static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** Daftar elemen node array (kosong bila bukan array). */
    public static List<JsonNode> elements(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        java.util.ArrayList<JsonNode> out = new java.util.ArrayList<>();
        node.forEach(out::add);
        return out;
    }
}
