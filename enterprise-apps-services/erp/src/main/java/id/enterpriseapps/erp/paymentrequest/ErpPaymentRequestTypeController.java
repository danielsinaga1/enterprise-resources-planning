package id.enterpriseapps.erp.paymentrequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.enterpriseapps.erp.paymentrequest.PrRequests.TypeInput;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Jenis Pengajuan Pembayaran — port {@code routes/paymentRequestTypes.ts}. Mount {@code requireWrite('settings')}
 * (ErpRouteGuard): GET terbuka untuk semua pengguna login (dibutuhkan formulir), tulis butuh settings edit.
 */
@RestController
@RequestMapping("/api/erp/payment-request-types")
@RequiredArgsConstructor
public class ErpPaymentRequestTypeController {

    private final ErpPaymentRequestTypeService types;

    /** GET / — jenis aktif; {@code ?all=1} (atau {@code true}) juga mengembalikan yang nonaktif. */
    @GetMapping
    public List<ObjectNode> list(@RequestParam Map<String, String> query) {
        String all = query.get("all");
        return types.list("1".equals(all) || "true".equals(all));
    }

    @PostMapping
    public ResponseEntity<ObjectNode> create(@RequestBody(required = false) JsonNode body) {
        TypeInput input = TypeInput.parse(body);
        return ResponseEntity.status(201).body(types.create(input));
    }

    @PutMapping("/{id}")
    public ObjectNode update(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        TypeInput input = TypeInput.parse(body);
        return types.update(id, input);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        return types.delete(id);
    }
}
