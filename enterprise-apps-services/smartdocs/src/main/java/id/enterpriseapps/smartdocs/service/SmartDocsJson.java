package id.enterpriseapps.smartdocs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

/**
 * Pembacaan kecil atas blob JSON milik mesin OCR.
 *
 * <p>Blob-nya sengaja tidak dimodelkan sebagai kelas: bentuknya masih berubah
 * mengikuti mesin OCR, dan modul ini hanya perlu beberapa field saja. Semua
 * pembacaan fail-soft — JSON rusak atau field hilang menghasilkan nilai kosong,
 * bukan kegagalan permintaan, karena data ini berasal dari OCR yang memang
 * kadang tidak lengkap.
 */
final class SmartDocsJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SmartDocsJson() {
    }

    static JsonNode parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    static BigDecimal number(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        try {
            return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText().trim());
        } catch (Exception e) {
            return null;
        }
    }
}
