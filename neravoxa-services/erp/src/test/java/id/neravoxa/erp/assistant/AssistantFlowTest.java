package id.neravoxa.erp.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.security.ErpPermissionService;
import id.neravoxa.erp.support.ErpIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Alur asisten end-to-end dengan model tiruan: RAG (unggah → cari → kutip), tool baca data yang
 * mengikuti izin pengguna, dan usulan input data yang body-nya benar-benar diterima endpoint ERP.
 */
@TestPropertySource(properties = {
        "app.assistant.hf-token=test-token",
        "app.assistant.async-ingest=false",
        "app.assistant.min-score=0.05",
        "app.assistant.requests-per-5-min=0"})
@Import(AssistantFlowTest.FakeModelsConfig.class)
class AssistantFlowTest extends ErpIntegrationTest {

    @Autowired
    FakeModels models;

    @Autowired
    ErpPermissionService permissions;

    @BeforeEach
    void reset() {
        models.script.clear();
        models.calls.clear();
        asAdmin();
    }

    @AfterEach
    void clearScript() {
        models.script.clear();
    }

    // ───────────────────────────── RAG ─────────────────────────────

    @Test
    void uploaded_document_is_retrieved_and_cited() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "sop-gudang.txt", "text/plain",
                ("# Kebijakan Gudang\n\nSetiap barang zebrakuda wajib diperiksa supervisor sebelum masuk gudang utama.\n\n"
                        + "# Lainnya\n\nRapat mingguan diadakan setiap Senin.").getBytes(StandardCharsets.UTF_8));
        JsonNode doc = ok(MockMvcRequestBuilders.multipart("/api/erp/assistant/documents").file(file).param("title", "SOP Gudang"));
        String docId = doc.get("id").asText();

        JsonNode list = ok(get("/api/erp/assistant/documents"));
        JsonNode mine = find(list.get("documents"), docId);
        assertEquals("ready", mine.get("status").asText());
        assertTrue(mine.get("chunkCount").asInt() >= 2);

        models.script.add(messages -> answer("Barang zebrakuda harus diperiksa supervisor dulu [1]."));
        JsonNode res = ok(post("/api/erp/assistant/chat", chatBody("zebrakuda", "/inventory")));

        assertEquals(docId, res.get("sources").get(0).get("documentId").asText());
        String system = models.calls.get(0).get(0).get("content").asText();
        assertTrue(system.contains("PENGETAHUAN"));
        assertTrue(system.contains("zebrakuda"));
        assertTrue(system.contains("/inventory - Stok"), "katalog halaman dari klien masuk ke prompt");

        assertEquals(200, call(delete("/api/erp/assistant/documents/" + docId)).status());
        assertEquals(404, call(delete("/api/erp/assistant/documents/" + docId)).status());
    }

    @Test
    void builtin_guide_is_indexed_at_startup() throws Exception {
        JsonNode list = ok(get("/api/erp/assistant/documents"));
        long guides = 0;
        for (JsonNode d : list.get("documents")) {
            if ("guide".equals(d.get("source").asText()) && "ready".equals(d.get("status").asText())) {
                guides++;
            }
        }
        assertTrue(guides >= 5, "panduan bawaan terindeks: " + guides);
    }

    // ───────────────────────────── tool baca data ─────────────────────────────

    @Test
    void read_tools_follow_user_permissions() throws Exception {
        models.script.add(messages -> toolCall("search_sales_invoices", "{\"limit\":3}"));
        models.script.add(messages -> answer("Berikut invoicenya."));
        ok(post("/api/erp/assistant/chat", chatBody("Invoice penjualan terbaru?", "/sales")));
        String adminTool = lastToolResult(models.calls.get(1));
        assertTrue(adminTool.contains("\"items\""), adminTool);

        asUser("produksi." + UUID.randomUUID() + "@test.local", "ERP_PRODUCTION");
        models.calls.clear();
        models.script.add(messages -> toolCall("search_sales_invoices", "{}"));
        models.script.add(messages -> answer("Maaf, tidak ada akses."));
        ok(post("/api/erp/assistant/chat", chatBody("Invoice penjualan?", "/")));
        assertTrue(lastToolResult(models.calls.get(1)).contains("tidak memiliki akses"));
    }

    // ───────────────────────────── usulan input data ─────────────────────────────

    @Test
    void cash_book_proposal_is_accepted_by_the_real_endpoint() throws Exception {
        String cashAccountId = ok(get("/api/erp/cash-book-drafts/accounts")).get("cashAccounts").get(0).get("id").asText();
        String today = ErpDates.businessToday().toString();
        String args = ErpJson.stringify(Map.of("entryDate", today, "cashAccountId", cashAccountId, "title", "Mutasi hari ini",
                "rows", List.of(
                        Map.of("date", today, "description", "Bayar listrik kantor", "keluar", 250000),
                        Map.of("description", "Terima transfer customer", "masuk", "1.500.000"))));
        models.script.add(messages -> toolCall("propose_cash_book_draft", args));
        models.script.add(messages -> answer("Usulan draft siap, silakan periksa."));

        JsonNode res = ok(post("/api/erp/assistant/chat", chatBody("Buatkan draft buku kas dari mutasi ini", "/cash-book-drafts")));
        JsonNode proposal = res.get("proposals").get(0);
        assertEquals("cash_book_draft", proposal.get("kind").asText());
        JsonNode body = proposal.get("body");
        assertEquals(1500000, body.get("rows").get(1).get("masuk").asInt());

        // Body usulan = kontrak endpoint asli: UI mengirimnya apa adanya saat pengguna menekan Buat.
        JsonNode created = ok(post("/api/erp/cash-book-drafts", ErpJson.mapper().treeToValue(body, Map.class)));
        String number = created.get("draftNumber").asText();

        models.script.add(messages -> toolCall("document_status", "{\"number\":\"" + number + "\"}"));
        models.script.add(messages -> answer("Tinjau lalu posting."));
        models.calls.clear();
        ok(post("/api/erp/assistant/chat", chatBody("Status " + number + "?", "/")));
        String status = lastToolResult(models.calls.get(1));
        assertTrue(status.contains("/cash-book-drafts/" + created.get("id").asText()), status);

        JsonNode suggestions = ok(get("/api/erp/assistant/suggestions?route=/cash-book-drafts")).get("suggestions");
        assertEquals("cashBookDrafts", suggestions.get(0).get("key").asText(), "saran halaman aktif tampil lebih dulu");
    }

    @Test
    void payment_request_and_inquiry_proposals_are_accepted_by_the_real_endpoints() throws Exception {
        String typeId = null;
        for (JsonNode t : ok(get("/api/erp/payment-request-types"))) {
            if ("OFFICE".equals(t.get("code").asText())) {
                typeId = t.get("id").asText();
            }
        }
        String prArgs = ErpJson.stringify(Map.of("typeId", typeId, "payeeName", "Budi", "purpose", "Beli ATK rapat",
                "lines", List.of(Map.of("description", "Kertas & tinta", "amount", 175000))));
        String inqArgs = ErpJson.stringify(Map.of("prospectName", "Toko Maju", "prospectPhone", "0812000111",
                "channel", "WhatsApp", "itemsText", "Beras premium 50 karung"));
        models.script.add(messages -> toolCalls(List.of(
                new AssistantModels.ToolCall("c1", "propose_payment_request", prArgs),
                new AssistantModels.ToolCall("c2", "propose_inquiry", inqArgs))));
        models.script.add(messages -> answer("Dua usulan siap."));

        JsonNode proposals = ok(post("/api/erp/assistant/chat", chatBody("Catat dua hal ini", "/"))).get("proposals");
        assertEquals(2, proposals.size());
        assertEquals("payment_request", proposals.get(0).get("kind").asText());
        assertEquals("inquiry", proposals.get(1).get("kind").asText());

        JsonNode pr = ok(post("/api/erp/payment-requests", ErpJson.mapper().treeToValue(proposals.get(0).get("body"), Map.class)));
        assertEquals("DRAFT", pr.get("status").asText());
        assertEquals(201, call(post("/api/erp/inquiries", ErpJson.mapper().treeToValue(proposals.get(1).get("body"), Map.class))).status());
    }

    @Test
    void invalid_proposal_is_reported_back_to_the_model() throws Exception {
        models.script.add(messages -> toolCall("propose_cash_book_draft",
                "{\"entryDate\":\"2026-10-01\",\"cashAccountId\":\"tidak-ada\",\"rows\":[{\"description\":\"x\",\"keluar\":1}]}"));
        models.script.add(messages -> answer("Akun kas tidak ditemukan."));
        JsonNode res = ok(post("/api/erp/assistant/chat", chatBody("Buat draft kas", "/")));
        assertEquals(0, res.get("proposals").size());
        assertTrue(lastToolResult(models.calls.get(1)).contains("cashAccountId tidak dikenal"));
    }

    // ───────────────────────────── izin kelola pengetahuan ─────────────────────────────

    @Test
    void knowledge_management_requires_settings_permission() throws Exception {
        asUser("viewer." + UUID.randomUUID() + "@test.local", "ERP_VIEWER");
        assertEquals(403, call(get("/api/erp/assistant/documents")).status());
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "isi".getBytes(StandardCharsets.UTF_8));
        assertEquals(403, call(MockMvcRequestBuilders.multipart("/api/erp/assistant/documents").file(file)).status());
        // Chat & saran tetap boleh untuk semua pengguna ERP.
        assertEquals(200, call(get("/api/erp/assistant/suggestions")).status());
        assertFalse(ok(get("/api/erp/assistant/status")).get("canManageKnowledge").asBoolean());
    }

    // ───────────────────────────── util ─────────────────────────────

    private static Map<String, Object> chatBody(String question, String route) {
        return Map.of("messages", List.of(Map.of("role", "user", "content", question)),
                "context", Map.of("route", route, "pageTitle", "Uji",
                        "pages", List.of(Map.of("path", "/inventory", "label", "Stok"), Map.of("path", "/sales", "label", "Invoice"))));
    }

    private static JsonNode find(JsonNode list, String id) {
        for (JsonNode n : list) {
            if (id.equals(n.get("id").asText())) {
                return n;
            }
        }
        throw new AssertionError("dokumen " + id + " tidak ada");
    }

    private static String lastToolResult(List<ObjectNode> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("tool".equals(messages.get(i).get("role").asText())) {
                return messages.get(i).get("content").asText();
            }
        }
        throw new AssertionError("tidak ada pesan tool");
    }

    static AssistantModels.ChatReply answer(String text) {
        ObjectNode msg = ErpJson.object();
        msg.put("role", "assistant");
        msg.put("content", text);
        return new AssistantModels.ChatReply(text, List.of(), msg);
    }

    static AssistantModels.ChatReply toolCall(String name, String args) {
        return toolCalls(List.of(new AssistantModels.ToolCall("call-" + name, name, args)));
    }

    static AssistantModels.ChatReply toolCalls(List<AssistantModels.ToolCall> calls) {
        ObjectNode msg = ErpJson.object();
        msg.put("role", "assistant");
        return new AssistantModels.ChatReply("", calls, msg);
    }

    /** Model tiruan: embedding bag-of-words (kata sama → vektor mirip) dan chat sesuai skrip per tes. */
    static class FakeModels implements AssistantModels {
        static final int DIM = 256;
        final Deque<Function<List<ObjectNode>, ChatReply>> script = new ArrayDeque<>();
        final List<List<ObjectNode>> calls = new ArrayList<>();

        @Override
        public float[][] embed(List<String> texts) {
            float[][] out = new float[texts.size()][];
            for (int i = 0; i < texts.size(); i++) {
                float[] v = new float[DIM];
                for (String w : texts.get(i).toLowerCase().replaceAll("^(query|passage): ", "").split("[^\\p{L}\\p{N}]+")) {
                    if (w.length() > 2) {
                        v[Math.floorMod(w.hashCode(), DIM)] += 1;
                    }
                }
                v[0] += 0.01f;
                out[i] = HuggingFaceModels.normalize(v);
            }
            return out;
        }

        @Override
        public ChatReply chat(List<ObjectNode> messages, ArrayNode tools) {
            calls.add(new ArrayList<>(messages));
            Function<List<ObjectNode>, ChatReply> step = script.poll();
            return step == null ? answer("selesai") : step.apply(messages);
        }
    }

    @TestConfiguration
    static class FakeModelsConfig {
        @Bean
        @Primary
        FakeModels fakeAssistantModels() {
            return new FakeModels();
        }
    }
}
