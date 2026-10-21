package id.enterpriseapps.assistant.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import id.enterpriseapps.auth.entity.AuthUser;
import id.enterpriseapps.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Tool BACA (modul Settings/Pengguna): mencari pengguna berdasarkan nama, NPK, atau email.
 * Berguna untuk menjawab "siapa NPK-nya X" dan untuk menemukan id pengguna sebelum menugaskan
 * task (lihat {@link CreateIncidentTaskTool}). Memakai {@code JpaSpecificationExecutor} bawaan
 * {@link AuthUserRepository} — tanpa menambah query baru.
 */
@Component
@RequiredArgsConstructor
public class SearchUsersTool implements AssistantTool {

    private final AuthUserRepository authUserRepository;

    @Override
    public String name() {
        return "cariPengguna";
    }

    @Override
    public String description() {
        return "Cari pengguna/karyawan berdasarkan nama, NPK, atau email. Kembalikan id, nama, NPK, dan email "
                + "(5 teratas). Pakai untuk menemukan id pengguna sebelum menugaskan task, atau menjawab "
                + "'siapa NPK/email si X'.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject schema = ToolSchemas.object();
        ToolSchemas.prop(schema, "keyword", "string", "Potongan nama, NPK, atau email yang dicari.");
        ToolSchemas.required(schema, "keyword");
        return schema;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public JsonObject execute(JsonObject args, AssistantUserContext user) {
        String keyword = ToolSchemas.optString(args, "keyword");
        if (keyword == null) {
            return ToolSchemas.error("Parameter 'keyword' wajib diisi.");
        }
        String like = "%" + keyword.toLowerCase() + "%";

        Specification<AuthUser> spec = (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("npk")), like),
                cb.like(cb.lower(root.get("email")), like));

        var page = authUserRepository.findAll(spec, PageRequest.of(0, 5));

        JsonObject result = new JsonObject();
        result.addProperty("total", page.getTotalElements());
        JsonArray items = new JsonArray();
        for (AuthUser u : page.getContent()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", u.getId());
            j.addProperty("nama", u.getName());
            j.addProperty("npk", u.getNpk());
            j.addProperty("email", u.getEmail());
            items.add(j);
        }
        result.add("items", items);
        return result;
    }
}
