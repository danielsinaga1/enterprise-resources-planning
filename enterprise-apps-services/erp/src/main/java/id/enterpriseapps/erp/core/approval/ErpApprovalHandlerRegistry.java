package id.enterpriseapps.erp.core.approval;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Kumpulan {@link ErpApprovalHandler} dari semua modul domain, diindeks per documentType. */
@Component
public class ErpApprovalHandlerRegistry {

    private final Map<String, ErpApprovalHandler> handlers = new HashMap<>();

    public ErpApprovalHandlerRegistry(List<ErpApprovalHandler> beans) {
        for (ErpApprovalHandler h : beans) {
            ErpApprovalHandler prev = handlers.put(h.documentType(), h);
            if (prev != null) {
                throw new IllegalStateException("Dua handler approval untuk " + h.documentType() + ": "
                        + prev.getClass().getName() + " & " + h.getClass().getName());
            }
        }
    }

    public Optional<ErpApprovalHandler> find(String documentType) {
        return Optional.ofNullable(handlers.get(documentType));
    }
}
