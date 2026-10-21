package id.enterpriseapps.erp.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.enterpriseapps.erp.core.ErpException;
import id.enterpriseapps.erp.entity.RootType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Uji unit semantik yang disalin dari sumber (urutan akun, angka JS, jadwal berulang, union Zod). */
class AccountingHelpersTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void account_numbers_sort_numerically_and_stably() {
        List<String> sorted = AccountNumbers.sorted(List.of("1.4.10", "1.4.2", "1", "2", "1.4", "10", "1.10", "1.2"),
                s -> s);
        assertEquals(List.of("1", "1.2", "1.4", "1.4.2", "1.4.10", "1.10", "2", "10"), sorted);
        assertTrue(AccountNumbers.compare("1.4.2", "1.4.10") < 0);
        assertEquals(0, AccountNumbers.compare("1.A", "1.B"));
    }

    @Test
    void root_type_derived_from_prefix() {
        assertEquals(RootType.ASSET, AccountNumbers.deriveRootType("1.1.9", RootType.LIABILITY));
        assertEquals(RootType.EXPENSE, AccountNumbers.deriveRootType("8.2", RootType.REVENUE));
        assertEquals(RootType.EQUITY, AccountNumbers.deriveRootType("9.1", RootType.EQUITY));
        assertNull(AccountNumbers.deriveRootType("10.1", null));
    }

    @Test
    void js_number_formatting_and_parsing() {
        assertEquals("100", JsNumbers.toJsString(100.0));
        assertEquals("0.30000000000000004", JsNumbers.toJsString(0.1 + 0.2));
        assertEquals("1500000.5", JsNumbers.toJsString(1500000.5));
        assertEquals("1e+21", JsNumbers.toJsString(1e21));
        assertEquals(0, JsNumbers.number("  "));
        assertEquals(16, JsNumbers.number("0x10"));
        assertTrue(Double.isNaN(JsNumbers.number("12abc")));
        assertEquals(new BigDecimal("1000.50"), ZodLite.coerce(json.getNodeFactory().textNode(" 1000.50 ")));
        assertNull(ZodLite.coerce(null));
        assertEquals(BigDecimal.ZERO, ZodLite.coerce(json.getNodeFactory().nullNode()));
    }

    @Test
    void next_run_date_follows_js_date_overflow() {
        Instant jan31 = Instant.parse("2026-01-31T05:00:00Z");
        assertEquals(Instant.parse("2026-03-03T05:00:00Z"), RecurringSchedule.next("monthly", jan31, null));
        assertEquals(Instant.parse("2026-03-31T05:00:00Z"), RecurringSchedule.next("monthly", jan31, 31));
        assertEquals(Instant.parse("2026-02-01T05:00:00Z"), RecurringSchedule.next("daily", jan31, null));
        assertEquals(Instant.parse("2026-04-30T05:00:00Z"),
                RecurringSchedule.next("quarterly", Instant.parse("2026-01-15T05:00:00Z"), 31));
        assertEquals(Instant.parse("2025-03-01T00:00:00Z"),
                RecurringSchedule.next("yearly", Instant.parse("2024-02-29T00:00:00Z"), null));
        // Frekuensi tak dikenal = +1 bulan tanpa penyesuaian tanggal.
        assertEquals(Instant.parse("2026-03-03T05:00:00Z"), RecurringSchedule.next("bogus", jan31, 15));
    }

    @Test
    void template_data_union_strips_unknown_keys_and_coerces() throws Exception {
        JsonNode out = RecurringTemplateData.parse(json.readTree("""
                {"narration":"Sewa","extra":1,"items":[
                 {"accountId":"5f0c2a8e-1b2c-4d3e-8f90-123456789abc","debit":"1500.5","credit":0,"x":true}]}"""));
        assertFalse(out.has("extra"));
        assertFalse(out.get("items").get(0).has("x"));
        assertEquals(1500.5, out.get("items").get(0).get("debit").asDouble());
        assertTrue(out.get("items").get(0).get("debit").isNumber());

        // Hanya opsi journal yang gagal karena cek nilai → pesannya dipakai.
        ErpException e = assertThrows(ErpException.class, () -> RecurringTemplateData.parse(json.readTree("""
                {"narration":"","items":[{"accountId":"5f0c2a8e-1b2c-4d3e-8f90-123456789abc","debit":1,"credit":0}]}""")));
        assertEquals("Narasi wajib diisi.", e.getMessage());
        ErpException e2 = assertThrows(ErpException.class, () -> RecurringTemplateData.parse(json.readTree("{}")));
        assertEquals("Invalid input", e2.getMessage());
        // Bentuk faktur lolos lewat opsi kedua.
        JsonNode inv = RecurringTemplateData.parse(json.readTree("{\"items\":[{\"itemName\":\"Jasa\",\"quantity\":\"2\",\"rate\":1000}]}"));
        assertEquals(2, inv.get("items").get(0).get("quantity").asInt());
    }

    @Test
    void zod_format_tree() {
        ZodLite.Issues is = new ZodLite.Issues();
        is.check(List.of("mappings", 0, "accountId"), ZodLite.INVALID_UUID);
        is.check(List.of(), "Tidak ada perubahan untuk disimpan.");
        JsonNode f = is.format();
        assertEquals("Invalid UUID", f.get("mappings").get("0").get("accountId").get("_errors").get(0).asText());
        assertEquals("Tidak ada perubahan untuk disimpan.", f.get("_errors").get(0).asText());
    }
}
