package id.neravoxa.erp.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.support.ErpIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tanpa HF_TOKEN: chat & unggah ditolak dengan pesan jelas, saran berbasis aturan tetap jalan. */
class AssistantNotConfiguredTest extends ErpIntegrationTest {

    @Test
    void chat_explains_missing_token_but_suggestions_still_work() throws Exception {
        asAdmin();
        assertFalse(ok(get("/api/erp/assistant/status")).get("configured").asBoolean());

        Result chat = call(post("/api/erp/assistant/chat",
                Map.of("messages", List.of(Map.of("role", "user", "content", "halo")))));
        assertEquals(503, chat.status());
        assertTrue(chat.error().contains("HF_TOKEN"));

        JsonNode suggestions = ok(get("/api/erp/assistant/suggestions?route=/"));
        assertTrue(suggestions.get("suggestions").isArray());
    }
}
