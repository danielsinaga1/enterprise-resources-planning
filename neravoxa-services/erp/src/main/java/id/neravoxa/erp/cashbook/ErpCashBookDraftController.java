package id.neravoxa.erp.cashbook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.security.ErpAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Draft Buku Kas — port {@code routes/cashBookDrafts.ts} (mount {@code /api/cash-book-drafts}; modul RBAC
 * 'journal': lihat = view, ubah/posting = edit — gerbang dijalankan ErpRouteGuard). Draft tidak berefek ke
 * GL sampai {@code POST /:id/post}.
 */
@RestController
@RequestMapping("/api/erp/cash-book-drafts")
@RequiredArgsConstructor
public class ErpCashBookDraftController {

    private final ErpCashBookDraftService drafts;
    private final ErpCashBookDraftQueryService queries;
    private final ErpCashBookPostService posting;
    private final ErpAccess access;

    /** Kanal MCP/AIRA tidak dipindahkan: semua permintaan datang dari UI. */
    private ErpCashBookDraftService.DraftActor actor() {
        return new ErpCashBookDraftService.DraftActor(access.userId(), "ui");
    }

    /** GET / — { data, total, page, limit, counts }. Query: page, limit, status, search, startDate, endDate. */
    @GetMapping({"", "/"})
    public Map<String, Object> list(@RequestParam Map<String, String> query) {
        return queries.list(query);
    }

    /** GET /accounts — katalog akun lawan + akun kas + aturan jenis transaksi + pola terpelajari. */
    @GetMapping("/accounts")
    public Map<String, Object> accounts() {
        return queries.accountsCatalog();
    }

    /** GET /learned-maps — pola terpelajari (keterangan ternormalisasi → akun). */
    @GetMapping("/learned-maps")
    public Map<String, Object> learnedMaps() {
        return ErpJson.map("data", queries.learnedMaps(500));
    }

    /** GET /:id — draft lengkap: baris, total masuk/keluar, jumlah perlu ditinjau, cek saldo. */
    @GetMapping("/{id}")
    public ObjectNode detail(@PathVariable String id) {
        ObjectNode detail = drafts.detail(id);
        if (detail == null) {
            throw ErpException.notFound(ErpCashBookDraftService.NOT_FOUND);
        }
        return detail;
    }

    /** POST / — buat draft dari baris (tempel/CSV). Tidak memposting apa pun. */
    @PostMapping({"", "/"})
    public ResponseEntity<ObjectNode> create(@RequestBody(required = false) JsonNode body) {
        CashBookZod.CreateDraft input = CashBookZod.createDraft(body);
        ErpCashBookDraftService.Created created = drafts.create(input, actor());
        ObjectNode detail = drafts.detail(created.id());
        detail.put("reviewPath", "/cash-book-drafts/" + created.id());
        detail.put("message", "Draft dibuat. Belum ada jurnal; tinjau dan posting di Entaris (Kas & Bank → Draft Buku Kas).");
        return ResponseEntity.status(201).body(detail);
    }

    /** PUT /:id — ubah header (judul, tanggal, akun kas, saldo awal/akhir, catatan) selagi belum Posted. */
    @PutMapping("/{id}")
    public ObjectNode update(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        CashBookZod.UpdateDraft patch = CashBookZod.updateDraft(body);
        drafts.updateHeader(id, patch);
        return drafts.detail(id);
    }

    /** PUT /:id/rows — ubah banyak baris sekaligus (akun, jenis, keterangan, nominal, tanggal, arah, skip, reviewed). */
    @PutMapping("/{id}/rows")
    public ObjectNode updateRows(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        var patches = CashBookZod.updateRows(body);
        Map<String, Object> result = drafts.updateRows(id, patches);
        return ErpJson.merge(result, ErpJson.map("draft", drafts.detail(id)));
    }

    /** POST /:id/rows — tambah baris (total maksimal 300 per draft). */
    @PostMapping("/{id}/rows")
    public ResponseEntity<ObjectNode> appendRows(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        var rows = CashBookZod.appendRows(body);
        Map<String, Object> result = drafts.appendRows(id, rows);
        return ResponseEntity.status(201).body(ErpJson.merge(result, ErpJson.map("draft", drafts.detail(id))));
    }

    /** POST /:id/post — posting: satu jurnal seimbang per baris non-skip. {partial?: boolean}. */
    @PostMapping("/{id}/post")
    public Map<String, Object> post(@PathVariable String id, @RequestBody(required = false) JsonNode body) {
        boolean partial = CashBookZod.postPartial(body);
        return posting.post(id, access.userId(), partial);
    }

    /** POST /:id/discard — buang draft (hanya status Draft). */
    @PostMapping("/{id}/discard")
    public Map<String, Object> discard(@PathVariable String id) {
        drafts.discard(id);
        return ErpJson.map("message", "Draft buku kas dibuang.");
    }
}
