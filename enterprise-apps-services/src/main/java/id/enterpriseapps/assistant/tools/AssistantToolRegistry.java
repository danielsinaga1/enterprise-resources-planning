package id.enterpriseapps.assistant.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mengumpulkan seluruh {@link AssistantTool} yang terdaftar sebagai bean dan menyajikannya
 * dalam dua bentuk yang dibutuhkan loop agent: (1) blok {@code tools} siap-pakai untuk request
 * Gemini, dan (2) lookup by-name saat Gemini meminta pemanggilan.
 */
@Component
public class AssistantToolRegistry {

    private final Map<String, AssistantTool> byName = new LinkedHashMap<>();

    public AssistantToolRegistry(List<AssistantTool> tools) {
        for (AssistantTool t : tools) {
            byName.put(t.name(), t);
        }
    }

    public AssistantTool find(String name) {
        return name == null ? null : byName.get(name);
    }

    public boolean isEmpty() {
        return byName.isEmpty();
    }

    /**
     * Bangun array {@code tools} untuk body generateContent:
     * {@code [{ "functionDeclarations": [ {name, description, parameters}, ... ] }]}.
     */
    public JsonArray toolsRequestArray() {
        JsonArray declarations = new JsonArray();
        for (AssistantTool t : byName.values()) {
            JsonObject decl = new JsonObject();
            decl.addProperty("name", t.name());
            decl.addProperty("description", t.description());
            JsonObject params = t.parameters();
            if (params != null) {
                decl.add("parameters", params);
            }
            declarations.add(decl);
        }
        JsonObject toolsEntry = new JsonObject();
        toolsEntry.add("functionDeclarations", declarations);
        JsonArray tools = new JsonArray();
        tools.add(toolsEntry);
        return tools;
    }
}
