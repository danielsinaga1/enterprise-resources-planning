package id.enterpriseapps.assistant;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Penyedia LLM alternatif untuk KPIntar memakai Claude (Anthropic), lewat SDK resmi
 * {@code com.anthropic:anthropic-java}. Aktif hanya bila {@code assistant.provider=claude};
 * selain itu {@link GeminiClient} yang dipakai.
 *
 * <p>Seluruh loop agent, registry tool, dan controller berbicara dalam format generateContent
 * Gemini (lihat {@link AssistantLlmClient}). Kelas ini menerjemahkannya bolak-balik ke bentuk
 * Messages API Anthropic, sehingga pergantian penyedia cukup satu properti dan tidak menyentuh
 * kode lain:
 *
 * <pre>
 *   contents[].parts[].text            &lt;-&gt; content blocks bertipe text
 *   contents[].parts[].functionCall    &lt;-&gt; content blocks bertipe tool_use
 *   contents[].parts[].functionResponse&lt;-&gt; content blocks bertipe tool_result
 *   tools[].functionDeclarations[]     &lt;-&gt; tools[] {name, description, input_schema}
 * </pre>
 *
 * <p>Masalah {@code thoughtSignature} yang ada di jalur Gemini tidak berlaku di sini: Claude
 * memasangkan panggilan tool lewat {@code tool_use_id}, bukan lewat tanda penalaran.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "assistant.provider", havingValue = "claude")
public class ClaudeClient implements AssistantLlmClient {

    private static final Gson GSON = new Gson();

    private final ClaudeProperties props;
    private final ObjectMapper objectMapper;

    private AnthropicClient client;

    // Konstruktor ditulis manual, bukan @RequiredArgsConstructor: di repo ini Lombok tidak
    // menyalin anotasi injeksi ke konstruktor yang dibangkitkannya (lihat CLAUDE.md), dan
    // kelas ini cukup sering disalin-tempel sebagai contoh sehingga lebih aman eksplisit.
    public ClaudeClient(ClaudeProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(props.getApiKey() == null ? "" : props.getApiKey())
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .build();
        log.info("KPIntar memakai penyedia Claude, model {}", props.getModel());
    }

    @Override
    public GeminiClient.GeminiResult generateWithTools(String systemPrompt, JsonArray contents, JsonArray tools) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new GeminiClient.AssistantException(
                    "Anthropic API key belum dikonfigurasi (set env ANTHROPIC_API_KEY).");
        }

        MessageCreateParams.Builder body = MessageCreateParams.builder()
                .model(props.getModel())
                .maxTokens(props.getMaxTokens());

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.system(systemPrompt);
        }
        for (Tool tool : toClaudeTools(tools)) {
            body.addTool(tool);
        }
        for (MessageParam message : toClaudeMessages(contents)) {
            body.addMessage(message);
        }

        try {
            return toResult(client.messages().create(body.build()));
        } catch (RateLimitException e) {
            log.error("Claude 429/kuota: {}", e.getMessage());
            throw new GeminiClient.AssistantException(
                    "Layanan AI sedang sibuk atau kuotanya habis. Coba lagi sebentar lagi. 🙏");
        } catch (AnthropicServiceException e) {
            // Saldo kredit habis juga mendarat di sini sebagai invalid_request_error.
            log.error("Claude API error: {}", e.getMessage());
            throw new GeminiClient.AssistantException("Permintaan ke layanan AI ditolak.");
        } catch (RuntimeException e) {
            log.warn("Claude API gagal dihubungi: {}", e.getMessage());
            throw new GeminiClient.AssistantException("Gagal menghubungi layanan AI (jaringan).");
        }
    }

    /* ------------------------- permintaan: Gemini -> Claude ------------------------- */

    /** {@code [{functionDeclarations:[{name, description, parameters}]}]} -> daftar {@link Tool}. */
    private List<Tool> toClaudeTools(JsonArray tools) {
        List<Tool> hasil = new ArrayList<>();
        if (tools == null) return hasil;

        for (JsonElement entry : tools) {
            if (!entry.isJsonObject()) continue;
            JsonArray declarations = entry.getAsJsonObject().getAsJsonArray("functionDeclarations");
            if (declarations == null) continue;

            for (JsonElement d : declarations) {
                if (!d.isJsonObject()) continue;
                JsonObject decl = d.getAsJsonObject();
                if (!decl.has("name")) continue;

                Tool.Builder tool = Tool.builder()
                        .name(decl.get("name").getAsString())
                        .inputSchema(toInputSchema(decl));
                if (decl.has("description") && !decl.get("description").isJsonNull()) {
                    tool.description(decl.get("description").getAsString());
                }
                hasil.add(tool.build());
            }
        }
        return hasil;
    }

    /**
     * Skema parameter tool di repo ini sudah JSON Schema biasa ({@code type: "object"} huruf
     * kecil), jadi isinya dipindahkan apa adanya — tidak ada konversi tipe yang perlu ditebak.
     */
    private Tool.InputSchema toInputSchema(JsonObject decl) {
        Tool.InputSchema.Builder schema = Tool.InputSchema.builder();
        if (!decl.has("parameters") || !decl.get("parameters").isJsonObject()) {
            return schema.build();
        }
        JsonObject parameters = decl.getAsJsonObject("parameters");

        JsonObject properties = parameters.getAsJsonObject("properties");
        if (properties != null) {
            Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
            for (Map.Entry<String, JsonElement> p : properties.entrySet()) {
                props.putAdditionalProperty(p.getKey(), JsonValue.from(toJava(p.getValue())));
            }
            schema.properties(props.build());
        }

        JsonArray required = parameters.getAsJsonArray("required");
        if (required != null) {
            List<String> names = new ArrayList<>();
            for (JsonElement r : required) names.add(r.getAsString());
            schema.required(names);
        }
        return schema.build();
    }

    /** {@code contents} Gemini -> daftar {@link MessageParam} Anthropic. */
    private List<MessageParam> toClaudeMessages(JsonArray contents) {
        List<MessageParam> hasil = new ArrayList<>();
        if (contents == null) return hasil;

        // Penghitung terpisah untuk panggilan dan balasan tool, dipakai hanya bila id tidak ada
        // (lihat pairingId). Keduanya berjalan seiring karena satu functionCall selalu diikuti
        // tepat satu functionResponse dengan nama tool yang sama.
        Map<String, Integer> nomorPanggilan = new HashMap<>();
        Map<String, Integer> nomorBalasan = new HashMap<>();

        for (JsonElement c : contents) {
            if (!c.isJsonObject()) continue;
            JsonObject entry = c.getAsJsonObject();
            String role = entry.has("role") ? entry.get("role").getAsString() : "user";
            JsonArray parts = entry.getAsJsonArray("parts");
            if (parts == null) continue;

            List<ContentBlockParam> blocks = new ArrayList<>();
            for (JsonElement p : parts) {
                if (!p.isJsonObject()) continue;
                JsonObject part = p.getAsJsonObject();

                if (part.has("functionCall") && part.get("functionCall").isJsonObject()) {
                    JsonObject fc = part.getAsJsonObject("functionCall");
                    if (!fc.has("name")) continue;
                    String name = fc.get("name").getAsString();
                    JsonElement args = fc.get("args");
                    blocks.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                            .id(pairingId(fc, name, nomorPanggilan))
                            .name(name)
                            .input(JsonValue.from(toJava(args != null && args.isJsonObject()
                                    ? args : new JsonObject())))
                            .build()));

                } else if (part.has("functionResponse") && part.get("functionResponse").isJsonObject()) {
                    JsonObject fr = part.getAsJsonObject("functionResponse");
                    String name = fr.has("name") ? fr.get("name").getAsString() : "tool";
                    JsonElement response = fr.get("response");
                    blocks.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                            .toolUseId(pairingId(fr, name, nomorBalasan))
                            .content(response == null || response.isJsonNull() ? "{}" : response.toString())
                            .build()));

                } else if (part.has("text") && !part.get("text").isJsonNull()) {
                    String text = part.get("text").getAsString();
                    if (!text.isBlank()) {
                        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(text).build()));
                    }
                }
            }

            // Anthropic menolak pesan tanpa isi; turn kosong (mis. part teks yang blank) dibuang.
            if (blocks.isEmpty()) continue;

            hasil.add(MessageParam.builder()
                    .role("model".equalsIgnoreCase(role) ? MessageParam.Role.ASSISTANT : MessageParam.Role.USER)
                    .contentOfBlockParams(blocks)
                    .build());
        }
        return hasil;
    }

    /**
     * Ambil id pemasangan tool. Anthropic MEWAJIBKAN {@code tool_use.id} dan
     * {@code tool_result.tool_use_id} yang cocok, sedangkan Gemini boleh tidak mengirim id sama
     * sekali. Bila id tidak ada, dibangkitkan dari nama tool + urutan kemunculannya; karena
     * panggilan dan balasan dihitung di peta yang berbeda tetapi dalam urutan yang sama,
     * pasangan ke-n dari satu tool selalu memperoleh id yang sama di kedua sisi.
     */
    private String pairingId(JsonObject node, String name, Map<String, Integer> penghitung) {
        if (node.has("id") && !node.get("id").isJsonNull()) {
            String id = node.get("id").getAsString();
            if (!id.isBlank()) return id;
        }
        int urutan = penghitung.merge(name, 1, Integer::sum);
        return "call_" + name + "_" + urutan;
    }

    /* ------------------------- balasan: Claude -> Gemini ------------------------- */

    private GeminiClient.GeminiResult toResult(Message response) {
        StringBuilder teks = new StringBuilder();
        List<GeminiClient.FunctionCall> calls = new ArrayList<>();

        for (ContentBlock block : response.content()) {
            Optional<ToolUseBlock> toolUse = block.toolUse();
            if (toolUse.isPresent()) {
                ToolUseBlock call = toolUse.get();
                // Claude kerap meminta beberapa tool sekaligus dalam satu balasan; semuanya
                // dikumpulkan, bukan hanya yang pertama. thoughtSignature null karena Claude
                // memasangkan panggilan lewat tool_use_id, bukan lewat tanda penalaran.
                calls.add(new GeminiClient.FunctionCall(
                        call.name(), call.id(), toGson(call._input()), null));
                continue;
            }
            block.text().ifPresent(t -> teks.append(t.text()));
        }

        String jawaban = teks.toString().trim();
        if (!calls.isEmpty()) {
            // Teks pengantar sebelum panggilan tool ikut dibawa agar tetap masuk riwayat.
            return GeminiClient.GeminiResult.functionCalls(calls, jawaban);
        }
        if (jawaban.isEmpty()) {
            log.warn("Claude membalas tanpa teks maupun tool (stopReason={})", response.stopReason());
            throw new GeminiClient.AssistantException("Layanan AI tidak mengembalikan jawaban.");
        }
        return GeminiClient.GeminiResult.text(jawaban);
    }

    /** Argumen tool dari SDK (Jackson) -> pohon Gson yang dipakai loop agent & registry tool. */
    private JsonObject toGson(JsonValue value) {
        try {
            JsonElement parsed = JsonParser.parseString(objectMapper.writeValueAsString(value));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            log.warn("Gagal membaca argumen tool dari Claude: {}", e.getMessage());
            return new JsonObject();
        }
    }

    private Object toJava(JsonElement element) {
        return GSON.fromJson(element, Object.class);
    }
}
