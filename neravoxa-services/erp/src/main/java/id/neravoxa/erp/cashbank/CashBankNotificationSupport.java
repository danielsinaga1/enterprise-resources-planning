package id.neravoxa.erp.cashbank;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpJson;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Helper bersama pemeriksaan notifikasi terjadwal slice kas &amp; bank. */
final class CashBankNotificationSupport {

    private CashBankNotificationSupport() {
    }

    /**
     * Nilai {@code metadata.<key>} dari notifikasi bertipe {@code type} sejak {@code since} — deduplikasi
     * 24 jam seperti {@code runNotificationChecks}.
     */
    static Set<String> notifiedIds(EntityManager em, String type, String key, Instant since) {
        Set<String> out = new HashSet<>();
        em.createQuery("select n.metadata from Notification n where n.type = :type and n.createdAt >= :since", JsonNode.class)
                .setParameter("type", type)
                .setParameter("since", since)
                .getResultList()
                .forEach(m -> {
                    String id = ErpJson.text(m, key);
                    if (id != null && !id.isEmpty()) {
                        out.add(id);
                    }
                });
        return out;
    }
}
