package id.enterpriseapps.assistant.dto;

/**
 * Hasil satu giliran chat agent: entah jawaban teks final ({@link #reply}), entah aksi tulis
 * yang menunggu konfirmasi pengguna ({@link #pendingAction}). Tepat satu di antaranya terisi.
 */
public class AssistantOutcome {

    private final String reply;
    private final PendingAction pendingAction;

    private AssistantOutcome(String reply, PendingAction pendingAction) {
        this.reply = reply;
        this.pendingAction = pendingAction;
    }

    public static AssistantOutcome reply(String reply) {
        return new AssistantOutcome(reply, null);
    }

    public static AssistantOutcome pending(PendingAction pendingAction) {
        return new AssistantOutcome(null, pendingAction);
    }

    public boolean needsConfirmation() {
        return pendingAction != null;
    }

    public String getReply() {
        return reply;
    }

    public PendingAction getPendingAction() {
        return pendingAction;
    }
}
