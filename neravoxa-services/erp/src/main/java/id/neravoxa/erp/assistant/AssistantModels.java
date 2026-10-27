package id.neravoxa.erp.assistant;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Model AI yang dipakai asisten: embedding (untuk RAG) dan chat dengan tool calling.
 * Implementasi produksi: {@link HuggingFaceModels}; tes memasang tiruan.
 */
public interface AssistantModels {

    /** Vektor embedding ter-normalisasi (panjang 1), satu per teks, urutan sama dengan input. */
    float[][] embed(List<String> texts);

    /**
     * Satu putaran chat. {@code messages} & {@code tools} berformat OpenAI Chat Completions;
     * {@code tools} boleh kosong.
     */
    ChatReply chat(List<ObjectNode> messages, ArrayNode tools);

    /** Isi jawaban model; {@code message} = pesan asisten mentah untuk diteruskan ke putaran berikut. */
    record ChatReply(String content, List<ToolCall> toolCalls, ObjectNode message) {

        public boolean wantsTools() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    record ToolCall(String id, String name, String arguments) {
    }
}
