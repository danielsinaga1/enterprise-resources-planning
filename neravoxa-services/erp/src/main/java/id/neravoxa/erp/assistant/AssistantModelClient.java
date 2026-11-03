package id.neravoxa.erp.assistant;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implementasi {@link AssistantModels} yang dipakai aplikasi: meneruskan ke Hugging Face atau Ollama sesuai
 * {@code app.assistant.provider}. Tes menimpanya dengan bean tiruan {@code @Primary}.
 */
@Component
public class AssistantModelClient implements AssistantModels {

    private final AssistantProperties props;
    private final HuggingFaceModels huggingFace;
    private final OllamaModels ollama;

    public AssistantModelClient(AssistantProperties props) {
        this.props = props;
        this.huggingFace = new HuggingFaceModels(props);
        this.ollama = new OllamaModels(props);
    }

    private AssistantModels active() {
        return props.useOllama() ? ollama : huggingFace;
    }

    @Override
    public float[][] embed(List<String> texts) {
        return active().embed(texts);
    }

    @Override
    public ChatReply chat(List<ObjectNode> messages, ArrayNode tools) {
        return active().chat(messages, tools);
    }
}
