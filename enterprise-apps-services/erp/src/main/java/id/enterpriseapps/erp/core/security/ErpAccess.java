package id.enterpriseapps.erp.core.security;

import id.enterpriseapps.erp.core.ErpException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Cek izin untuk pengguna saat ini — padanan {@code can()/hasCap()/requireCap()/needsApproval()}
 * di sumber ENTARIS. Gerbang per-modul di level rute (requirePerm/requireWrite di index.ts)
 * sudah dijalankan {@code ErpRouteGuard}; kelas ini untuk cek tambahan di dalam handler.
 */
@Component
@RequiredArgsConstructor
public class ErpAccess {

    private final ErpActorResolver actors;
    private final ErpPermissionService permissions;

    public ErpActor actor() {
        return actors.current();
    }

    /** id pengguna ERP saat ini — padanan {@code req.user!.userId}. */
    public String userId() {
        return actors.current().userId();
    }

    public boolean can(String module, String action) {
        return permissions.can(actor().roleKeys(), module, action);
    }

    public boolean can(ErpActor actor, String module, String action) {
        return permissions.can(actor.roleKeys(), module, action);
    }

    public boolean hasCap(String capability) {
        return permissions.hasCap(actor().roleKeys(), capability);
    }

    public boolean hasCap(ErpActor actor, String capability) {
        return permissions.hasCap(actor.roleKeys(), capability);
    }

    public boolean isSuperAdmin() {
        return permissions.isSuperAdmin(actor().roleKeys());
    }

    /** 403 bila tidak punya capability — padanan middleware {@code requireCap(cap)}. */
    public void requireCap(String capability) {
        if (!hasCap(capability)) {
            throw ErpException.forbidden();
        }
    }

    /** 403 bila tidak punya izin modul. */
    public void requireCan(String module, String action) {
        if (!can(module, action)) {
            throw ErpException.forbidden();
        }
    }

    /** Lolos bila punya izin salah satu modul. */
    public void requireAny(List<String> modules, String action) {
        for (String m : modules) {
            if (can(m, action)) {
                return;
            }
        }
        throw ErpException.forbidden();
    }

    /** Role tanpa capability {@code approval.bypass} → dokumen masuk antrian approval. */
    public boolean needsApproval() {
        return !hasCap("approval.bypass");
    }

    public boolean needsApproval(ErpActor actor) {
        return !hasCap(actor, "approval.bypass");
    }

    /** Izin lengkap untuk klien. */
    public Map<String, Object> effectiveForClient() {
        return permissions.effectiveForClient(actor().roleKeys());
    }
}
