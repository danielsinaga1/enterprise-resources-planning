package id.neravoxa.erp.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Klien Ollama terhadap server tiruan berformat API kompatibel OpenAI-nya (tanpa Spring). */
class OllamaModelsTest {

    private HttpServer server;
    private final List<JsonNode> requests = new ArrayList<>();
    private AssistantProperties props;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", ex -> {
            JsonNode body = ErpJson.mapper().readTree(ex.getRequestBody());
            requests.add(body);
            if ("missing".equals(body.path("model").asText())) {
                reply(ex, 404, "{\"error\":{\"message\":\"model \\\"missing\\\" not found, try pulling it first\"}}");
                return;
            }
            // Urutan dibalik: klien harus menyusun ulang menurut index.
            StringBuilder data = new StringBuilder();
            int n = body.path("input").size();
            for (int i = n - 1; i >= 0; i--) {
                data.append(data.isEmpty() ? "" : ",").append("{\"index\":").append(i).append(",\"embedding\":[").append(i + 1).append(",0]}");
            }
            reply(ex, 200, "{\"data\":[" + data + "]}");
        });
        server.createContext("/v1/chat/completions", ex -> {
            requests.add(ErpJson.mapper().readTree(ex.getRequestBody()));
            reply(ex, 200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"c1\","
                    + "\"type\":\"function\",\"function\":{\"name\":\"search_guide\",\"arguments\":\"{\\\"query\\\":\\\"jam meter\\\"}\"}}]}}]}");
        });
        server.start();
        props = new AssistantProperties();
        props.setProvider("ollama");
        props.getOllama().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    void provider_settings_switch_models_prefixes_and_threshold() {
        assertTrue(props.configured());
        assertEquals("ollama", props.activeProvider());
        assertEquals("bge-m3", props.activeEmbeddingModel());
        assertEquals("qwen2.5:7b", props.activeChatModel());
        assertEquals("", props.activeQueryPrefix());
        assertEquals(0.45, props.activeMinScore());

        AssistantProperties hf = new AssistantProperties();
        assertFalse(hf.configured());
        assertEquals("query: ", hf.activeQueryPrefix());
        assertEquals("intfloat/multilingual-e5-large", hf.activeEmbeddingModel());
    }

    @Test
    void embeddings_are_ordered_by_index_and_normalized() {
        float[][] v = new AssistantModelClient(props).embed(List.of("a", "b", "c"));
        assertEquals(3, v.length);
        assertEquals(1.0f, v[0][0], 1e-6);
        assertEquals(1.0f, v[2][0], 1e-6);
        JsonNode sent = requests.get(0);
        assertEquals("bge-m3", sent.path("model").asText());
        assertEquals(3, sent.path("input").size());
        assertEquals("a", sent.path("input").get(0).asText());
    }

    @Test
    void chat_sends_tools_and_parses_tool_calls() {
        ObjectNode user = ErpJson.object();
        user.put("role", "user");
        user.put("content", "Bagaimana mencatat jam meter?");
        var tools = ErpJson.arrayNode();
        tools.addObject().put("type", "function").putObject("function").put("name", "search_guide");
        AssistantModels.ChatReply reply = new AssistantModelClient(props).chat(List.of(user), tools);
        assertTrue(reply.wantsTools());
        assertEquals("search_guide", reply.toolCalls().get(0).name());
        assertEquals("{\"query\":\"jam meter\"}", reply.toolCalls().get(0).arguments());
        JsonNode sent = requests.get(0);
        assertEquals("qwen2.5:7b", sent.path("model").asText());
        assertEquals("auto", sent.path("tool_choice").asText());
    }

    @Test
    void friendly_errors_for_missing_model_and_unreachable_server() {
        props.getOllama().setEmbeddingModel("missing");
        ErpException missing = assertThrows(ErpException.class, () -> new OllamaModels(props).embed(List.of("x")));
        assertTrue(missing.getMessage().contains("ollama pull missing"), missing.getMessage());

        server.stop(0);
        ErpException down = assertThrows(ErpException.class, () -> new OllamaModels(props).embed(List.of("x")));
        assertTrue(down.getMessage().contains("Ollama tidak dapat dihubungi"), down.getMessage());
    }
}
