package id.enterpriseapps.assistant.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Pembantu kecil untuk menyusun skema parameter tool (subset OpenAPI ala Gemini) dan membaca argumen. */
final class ToolSchemas {

    private ToolSchemas() {}

    /**
     * Serialkan objek (DTO) ke pohon JSON Gson lewat Jackson milik aplikasi — Jackson sudah punya
     * modul jsr310 sehingga {@code LocalDateTime} dsb. aman (Gson polos gagal pada java.time di JDK17).
     * Dipakai tool LIMS yang mengembalikan DTO.
     */
    static JsonElement toJsonTree(ObjectMapper mapper, Object value) {
        try {
            return JsonParser.parseString(mapper.writeValueAsString(value));
        } catch (Exception e) {
            return error("Gagal menserialkan hasil: " + e.getMessage());
        }
    }

    static JsonObject object() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "object");
        o.add("properties", new JsonObject());
        return o;
    }

    static JsonObject prop(JsonObject schema, String name, String type, String description) {
        JsonObject p = new JsonObject();
        p.addProperty("type", type);
        p.addProperty("description", description);
        schema.getAsJsonObject("properties").add(name, p);
        return schema;
    }

    static JsonObject required(JsonObject schema, String... names) {
        JsonArray req = new JsonArray();
        for (String n : names) req.add(n);
        schema.add("required", req);
        return schema;
    }

    static String optString(JsonObject args, String key) {
        if (args == null || !args.has(key) || args.get(key).isJsonNull()) return null;
        String v = args.get(key).getAsString();
        return v == null || v.isBlank() ? null : v.trim();
    }

    static Integer optInt(JsonObject args, String key) {
        if (args == null || !args.has(key) || args.get(key).isJsonNull()) return null;
        try {
            return args.get(key).getAsInt();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Long optLong(JsonObject args, String key) {
        if (args == null || !args.has(key) || args.get(key).isJsonNull()) return null;
        try {
            return args.get(key).getAsLong();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Boolean optBool(JsonObject args, String key) {
        if (args == null || !args.has(key) || args.get(key).isJsonNull()) return null;
        try {
            return args.get(key).getAsBoolean();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Normalkan argumen tanggal <i>di tempat</i> menjadi {@code yyyy-MM-dd} — satu-satunya bentuk
     * yang diterima parser service IRMS/LIMS. Dipanggil dari {@code normalizeArgs} tiap tool,
     * sebelum kartu konfirmasi dibuat, supaya tanggal yang salah ketahuan selagi model masih
     * bisa memperbaikinya (bukan setelah pengguna menekan "Setujui").
     *
     * @throws IllegalArgumentException bila argumen terisi tetapi tidak bisa dibaca sebagai tanggal
     */
    static void canonDate(JsonObject args, String key) {
        String raw = optString(args, key);
        if (raw == null) return;
        String iso = AssistantDates.normalizeDate(raw);
        if (iso == null) {
            throw new IllegalArgumentException("Tanggal \"" + raw + "\" pada argumen '" + key
                    + "' tidak dikenali. Isi dengan format YYYY-MM-DD, mis. "
                    + java.time.LocalDate.now().format(AssistantDates.ISO_DATE) + ".");
        }
        args.addProperty(key, iso);
    }

    /** Seperti {@link #canonDate} tetapi untuk waktu, dinormalkan ke {@code HH:mm} 24 jam. */
    static void canonTime(JsonObject args, String key) {
        String raw = optString(args, key);
        if (raw == null) return;
        String hhmm = AssistantDates.normalizeTime(raw);
        if (hhmm == null) {
            throw new IllegalArgumentException("Waktu \"" + raw + "\" pada argumen '" + key
                    + "' tidak dikenali. Isi dengan format HH:mm 24 jam, mis. 14:30.");
        }
        args.addProperty(key, hhmm);
    }

    /** Pastikan argumen wajib terisi; pesannya dibaca model agar ia menanyakannya ke pengguna. */
    static void requireArgs(JsonObject args, String... keys) {
        for (String key : keys) {
            if (optString(args, key) == null && optLong(args, key) == null) {
                throw new IllegalArgumentException("Argumen '" + key + "' wajib diisi. "
                        + "Tanyakan nilainya ke pengguna bila belum disebut.");
            }
        }
    }

    /** Bungkus pesan error tool agar konsisten dikembalikan ke Gemini / pengguna. */
    static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o;
    }
}
