package id.enterpriseapps.assistant;

import com.google.gson.JsonObject;
import id.enterpriseapps.assistant.dto.AssistantOutcome;
import id.enterpriseapps.assistant.dto.ChatRequest;
import id.enterpriseapps.assistant.dto.ConfirmActionRequest;
import id.enterpriseapps.assistant.tools.AssistantUserContext;
import id.enterpriseapps.auth.entity.AuthUser;
import id.enterpriseapps.auth.repository.AuthUserRepository;
import id.enterpriseapps.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend untuk widget global "KPIntar" (EnterpriseAI). Selain memproksi chat ke Gemini (agar API
 * key tidak pernah sampai ke browser), controller ini menjalankan mode <b>agentic</b>: Gemini bisa
 * memanggil tool untuk membaca data IRMS atau membuat task tindak lanjut. Orkestrasi loop function
 * calling ada di {@link AssistantAgentService}; controller hanya menyediakan identitas pengguna dan
 * membungkus hasilnya.
 *
 * <p>Aksi tulis tidak langsung dieksekusi: {@code /chat} mengembalikan {@code pendingAction} untuk
 * dikonfirmasi pengguna, lalu {@code /action/confirm} menjalankannya atas nama pengguna yang login.
 */
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
@Tag(name = "Enterprise AI Assistant", description = "Widget KPIntar — chat + agentic actions (Gemini-backed)")
public class EnterpriseAssistantController {

    private final AssistantAgentService agentService;
    private final AuthUserRepository authUserRepository;

    @PostMapping("/chat")
    @Operation(summary = "Ajukan pertanyaan/aksi ke widget KPIntar")
    public ResponseEntity<Response> chat(@Valid @RequestBody ChatRequest request) {
        AssistantUserContext user = currentUser();
        AssistantOutcome outcome = agentService.run(request.getSystemPrompt(), request.getMessages(), user);

        Map<String, Object> data = new HashMap<>();
        if (outcome.needsConfirmation()) {
            data.put("pendingAction", outcome.getPendingAction());
        } else {
            data.put("reply", outcome.getReply());
        }
        return ResponseEntity.ok(Response.builder()
                .status("success")
                .httpStatus(HttpStatus.OK)
                .data(data)
                .build());
    }

    @PostMapping("/action/confirm")
    @Operation(summary = "Setujui & jalankan aksi tulis yang diusulkan KPIntar")
    public ResponseEntity<Response> confirmAction(@Valid @RequestBody ConfirmActionRequest request) {
        AssistantUserContext user = currentUser();
        try {
            JsonObject result = agentService.confirmAndExecute(request.getTool(), request.getArguments(), user);
            String message = result.has("pesan") && !result.get("pesan").isJsonNull()
                    ? result.get("pesan").getAsString()
                    : (result.has("error") ? result.get("error").getAsString() : "Aksi selesai dijalankan.");
            boolean ok = !result.has("error")
                    && (!result.has("berhasil") || result.get("berhasil").getAsBoolean());
            return ResponseEntity.ok(Response.builder()
                    .status(ok ? "success" : "FAILED")
                    .httpStatus(HttpStatus.OK)
                    .message(message)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Response.builder()
                    .status("FAILED")
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .errorCode("400")
                    .errorMessage(e.getMessage())
                    .build());
        }
    }

    /**
     * Bangun konteks pengguna dari Spring Security. Subject JWT = email (lihat AuthService),
     * jadi id numerik diresolusi lewat AuthUserRepository. Bila tidak ketemu, id null — tool tulis
     * akan menolak dengan pesan ramah, tool baca tetap jalan.
     */
    private AssistantUserContext currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth != null ? auth.getName() : null;

        List<String> roles = auth == null ? List.of()
                : auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                        .toList();

        Long userId = null;
        if (email != null) {
            userId = authUserRepository.findByEmail(email).map(AuthUser::getId).orElse(null);
        }
        return new AssistantUserContext(userId, email, roles);
    }

    @ExceptionHandler(GeminiClient.AssistantException.class)
    public ResponseEntity<Response> handleAssistantError(GeminiClient.AssistantException ex) {
        // errorCode dipakai widget untuk memutuskan perlu-tidaknya mencoba ulang; tanpa itu
        // semua galat AI tampak sama dan browser mengulang permintaan yang sudah pasti gagal.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Response.builder()
                .status("FAILED")
                .httpStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .errorCode(ex.getCode() != null ? ex.getCode() : "503")
                .errorMessage(ex.getMessage())
                .build());
    }
}
