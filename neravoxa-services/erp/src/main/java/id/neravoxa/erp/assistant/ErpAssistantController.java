package id.neravoxa.erp.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.security.ErpAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Asisten AI ERP. Rute {@code assistant} berstatus OPEN di {@code ErpRouteGuard}: semua pengguna ERP
 * boleh chat & melihat saran (data yang dibaca tetap mengikuti izinnya); kelola basis pengetahuan
 * butuh izin modul {@code settings}.
 */
@RestController
@RequestMapping("/api/erp/assistant")
@RequiredArgsConstructor
@Tag(name = "ERP - Asisten AI", description = "Chat RAG, saran langkah berikutnya, dan basis pengetahuan")
public class ErpAssistantController {

    private final AssistantChatService chat;
    private final KnowledgeService knowledge;
    private final NextStepAdvisor advisor;
    private final AssistantProperties props;
    private final ErpAccess access;

    @GetMapping("/status")
    @Operation(summary = "Status konfigurasi asisten (tanpa membuka token)")
    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configured", props.configured());
        out.put("provider", props.activeProvider());
        out.put("chatModel", props.activeChatModel());
        out.put("embeddingModel", props.activeEmbeddingModel());
        out.put("canManageKnowledge", access.can("settings", "edit"));
        return out;
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Saran langkah berikutnya untuk halaman aktif (berbasis aturan, tanpa model AI)")
    public Map<String, Object> suggestions(@RequestParam(required = false) String route) {
        return ErpJson.map("suggestions", advisor.suggestions(route));
    }

    @PostMapping("/chat")
    @Operation(summary = "Satu giliran chat: { messages: [{role, content}], context: { route, pageTitle, pages } }")
    public Map<String, Object> chat(@RequestBody(required = false) JsonNode body) {
        return chat.chat(body);
    }

    @GetMapping("/documents")
    @Operation(summary = "Daftar dokumen basis pengetahuan + statistik indeks")
    public Map<String, Object> documents() {
        access.requireCan("settings", "view");
        return knowledge.overview();
    }

    @PostMapping("/documents")
    @Operation(summary = "Unggah dokumen (PDF, DOCX, TXT, MD, CSV) ke basis pengetahuan; embedding diproses di latar belakang")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "title", required = false) String title) {
        access.requireCan("settings", "edit");
        return ResponseEntity.status(HttpStatus.CREATED).body(knowledge.upload(file, title, access.userId()));
    }

    @PostMapping("/documents/reindex-guide")
    @Operation(summary = "Indeks ulang panduan bawaan Neravoxa")
    public Map<String, Object> reindexGuide() {
        access.requireCan("settings", "edit");
        return knowledge.reindexGuide();
    }

    @PostMapping("/documents/{id}/reindex")
    @Operation(summary = "Hitung ulang embedding satu dokumen (mis. setelah model embedding diganti)")
    public Map<String, Object> reindex(@PathVariable String id) {
        access.requireCan("settings", "edit");
        return knowledge.reindex(id);
    }

    @DeleteMapping("/documents/{id}")
    @Operation(summary = "Hapus dokumen dari basis pengetahuan")
    public Map<String, Object> delete(@PathVariable String id) {
        access.requireCan("settings", "edit");
        knowledge.delete(id);
        return ErpJson.map("deleted", true);
    }
}
