package id.neravoxa.erp.core.accounting;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.entity.Payment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detail alokasi pembayaran — port {@code services/paymentAllocationDetail.ts}.
 *
 * <ul>
 *   <li>{@link #paymentForAllocation(Payment)} = bentuk {@code PAYMENT_ALLOCATION_PAYMENT_SELECT}
 *       (id, paymentNumber, date, amount, referenceNo, notes, status, account{…}, splits[{…, account}])
 *       yang dikembalikan detail invoice per alokasi. Pemanggil wajib sudah me-fetch
 *       {@code account}, {@code splits} dan {@code splits.account}.</li>
 *   <li>{@link #withAllocationInvoiceNumbers(Collection)} menambahkan {@code invoiceNumber} ke tiap
 *       alokasi polimorfik (invoiceType, invoiceId).</li>
 * </ul>
 */
@Service
public class ErpAllocationService {

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    private static Map<String, Object> account(id.neravoxa.erp.entity.Account a) {
        if (a == null || !Hibernate.isInitialized(a)) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("accountNumber", a.getAccountNumber());
        m.put("name", a.getName());
        m.put("rootType", a.getRootType());
        return m;
    }

    /** Bentuk pembayaran ringkas untuk blok "Rincian Pembayaran" invoice. */
    public Map<String, Object> paymentForAllocation(Payment p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("paymentNumber", p.getPaymentNumber());
        m.put("date", p.getDate());
        m.put("amount", p.getAmount());
        m.put("referenceNo", p.getReferenceNo());
        m.put("notes", p.getNotes());
        m.put("status", p.getStatus());
        m.put("account", account(p.getAccount()));
        List<Map<String, Object>> splits = p.getSplits() == null || !Hibernate.isInitialized(p.getSplits())
                ? List.of()
                : p.getSplits().stream().map(s -> {
                    Map<String, Object> sm = new LinkedHashMap<>();
                    sm.put("id", s.getId());
                    sm.put("amount", s.getAmount());
                    sm.put("notes", s.getNotes());
                    sm.put("account", account(s.getAccount()));
                    return sm;
                }).toList();
        m.put("splits", splits);
        return m;
    }

    /**
     * Tambahkan {@code invoiceNumber} (null bila invoice sudah tidak ada) ke tiap alokasi.
     * {@code allocations} boleh entity PaymentAllocation atau Map/JsonNode dengan invoiceType+invoiceId.
     */
    @ErpTx(readOnly = true)
    public ArrayNode withAllocationInvoiceNumbers(Collection<?> allocations) {
        ArrayNode arr = ErpJson.array(allocations);
        Set<String> salesIds = idsOfType(arr, "SalesInvoice");
        Set<String> purchaseIds = idsOfType(arr, "PurchaseInvoice");
        Map<String, String> numbers = new HashMap<>();
        if (!salesIds.isEmpty()) {
            em.createQuery("select s.id, s.invoiceNumber from SalesInvoice s where s.id in :ids", Object[].class)
                    .setParameter("ids", salesIds).getResultList()
                    .forEach(r -> numbers.put("SalesInvoice:" + r[0], (String) r[1]));
        }
        if (!purchaseIds.isEmpty()) {
            em.createQuery("select p.id, p.invoiceNumber from PurchaseInvoice p where p.id in :ids", Object[].class)
                    .setParameter("ids", purchaseIds).getResultList()
                    .forEach(r -> numbers.put("PurchaseInvoice:" + r[0], (String) r[1]));
        }
        arr.forEach(n -> {
            if (n instanceof ObjectNode o) {
                String key = ErpJson.text(o, "invoiceType") + ":" + ErpJson.text(o, "invoiceId");
                String num = numbers.get(key);
                if (num == null) {
                    o.putNull("invoiceNumber");
                } else {
                    o.put("invoiceNumber", num);
                }
            }
        });
        return arr;
    }

    private static Set<String> idsOfType(ArrayNode arr, String type) {
        return java.util.stream.StreamSupport.stream(arr.spliterator(), false)
                .filter(n -> type.equals(ErpJson.text(n, "invoiceType")))
                .map(n -> ErpJson.text(n, "invoiceId"))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
