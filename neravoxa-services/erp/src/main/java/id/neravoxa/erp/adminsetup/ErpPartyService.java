package id.neravoxa.erp.adminsetup;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.entity.Party;
import id.neravoxa.erp.entity.PartyType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mitra (pelanggan/vendor) — port {@code routes/parties.ts} + {@code services/partyList.ts}.
 * Saldo mitra (outstanding/uang muka) hanya dibaca di sini; diubah lewat posting dokumen
 * (ErpPartyBalanceService), tidak pernah lewat form mitra.
 */
@Slf4j
@Service
public class ErpPartyService {

    static final String LIST_ERROR = "Gagal mengambil data pihak.";

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    /**
     * GET /parties — dua mode dengan amplop {@code { data, total, page, limit }}:
     * mode daftar (ada search/sort/isActive/hasOutstanding; limit ≤ 500) dan mode lama
     * ({@code ?type=Customer&limit=…}, limit ≤ 200, urut nama).
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> list(Map<String, String> query) {
        if (PartyList.isListQuery(query)) {
            PartyList.Where built = PartyList.build(query);
            ListQuery.Paging paging = ListQuery.paging(query, 25, 500);
            ErpQuery<Party> q = built.where().applyTo(ErpQuery.of(em, Party.class, "p"))
                    .orderBy(PartyList.orderBy(query.get("sort"), built.type()));
            long total = q.count();
            return ListQuery.result(q.list(paging.skip(), paging.limit()), total, paging);
        }
        return legacyList(query);
    }

    /**
     * Mode lama: {@code take = min(Number(limit) || 100, 200)}, {@code skip = (Number(page) - 1) * take}.
     * Nilai yang di sumber membuat Prisma gagal (page bukan bilangan bulat ≥ 1, jenis tak dikenal)
     * dijawab 500 dengan pesan yang sama.
     */
    private Map<String, Object> legacyList(Map<String, String> query) {
        String type = query.get("type");
        double limitNum = AdminZod.jsNumber(query.getOrDefault("limit", "100"));
        double take = Math.min(Double.isNaN(limitNum) || limitNum == 0 ? 100 : limitNum, 200);
        double page = AdminZod.jsNumber(query.getOrDefault("page", "1"));
        double skip = (page - 1) * take;
        if (Double.isNaN(skip) || skip < 0 || skip != Math.rint(skip) || take < 0 || take != Math.rint(take)) {
            throw ErpException.of(500, LIST_ERROR);
        }
        JpqlWhere where = new JpqlWhere();
        if (type != null && !type.isEmpty()) {
            PartyType t = PartyList.parseType(type);
            if (t == null) {
                throw ErpException.of(500, LIST_ERROR);
            }
            // partyType Both ikut muncul saat filter Customer/Supplier (dipakai dropdown kedua sisi).
            if (t == PartyType.Both) {
                where.and("p.partyType = :ptBoth", "ptBoth", PartyType.Both);
            } else {
                where.and("p.partyType in (:ptType, :ptBoth)", "ptType", t, "ptBoth", PartyType.Both);
            }
        }
        ErpQuery<Party> q = where.applyTo(ErpQuery.of(em, Party.class, "p")).orderBy("p.name asc");
        long total = q.count();
        List<Party> rows = q.list((int) skip, (int) take);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", rows);
        out.put("total", total);
        out.put("page", (long) page);
        out.put("limit", (int) take);
        return out;
    }

    /** GET /parties/summary — KPI halaman daftar dengan filter yang sama. */
    @ErpTx(readOnly = true)
    public Map<String, Object> summary(Map<String, String> query) {
        PartyList.Where built = PartyList.build(query);
        TypedQuery<Object[]> q = em.createQuery("select p.partyType, p.isActive, p.outstandingAmount, p.depositBalance,"
                + " p.customerDepositBalance from Party p" + built.where().clause(), Object[].class);
        built.where().bind(q);
        return PartyList.summarize(q.getResultList());
    }

    /**
     * GET /parties/similar?name=&type= — maks 5 mitra mirip (substring/awal kata + Levenshtein,
     * skor ≥ 0.6) untuk peringatan duplikat saat membuat mitra.
     */
    @ErpTx(readOnly = true)
    public Map<String, Object> similar(String rawName, String rawType) {
        String name = rawName == null ? "" : rawName.strip();
        if (name.length() < 2) {
            return Map.of("data", List.of());
        }
        String lower = name.toLowerCase(Locale.ROOT);
        String firstWord = lower.split("\\s+")[0];
        JpqlWhere where = new JpqlWhere()
                .and("lower(p.name) like :contains" + JpqlWhere.ESC + " or lower(p.name) like :starts" + JpqlWhere.ESC,
                        "contains", JpqlWhere.contains(name), "starts", JpqlWhere.startsWith(firstWord));
        PartyType type = rawType == null ? null : PartyList.parseType(rawType);
        if (type != null) {
            where.and("p.partyType = :ptType or p.partyType = :ptBoth", "ptType", type, "ptBoth", PartyType.Both);
        }
        TypedQuery<Object[]> q = em.createQuery(
                "select p.id, p.name, p.partyType, p.phone, p.isActive from Party p" + where.clause(), Object[].class);
        where.bind(q);
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Object[] r : q.setMaxResults(50).getResultList()) {
            String pn = ((String) r[1]).toLowerCase(Locale.ROOT);
            int dist = PartyList.levenshtein(lower, pn);
            int maxLen = Math.max(lower.length(), pn.length());
            double similarity = maxLen == 0 ? 1 : 1 - (double) dist / maxLen;
            if (similarity < 0.6) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("name", r[1]);
            m.put("partyType", r[2]);
            m.put("phone", r[3]);
            m.put("isActive", r[4]);
            m.put("similarity", similarity);
            scored.add(m);
        }
        scored.sort(Comparator.comparingDouble((Map<String, Object> m) -> (Double) m.get("similarity")).reversed());
        return Map.of("data", scored.size() > 5 ? scored.subList(0, 5) : scored);
    }

    /** GET /parties/:id — satu mitra (termasuk cache outstanding) untuk modal refund/offset uang muka. */
    @ErpTx(readOnly = true)
    public Party get(String id) {
        Party p = em.find(Party.class, id);
        if (p == null) {
            throw ErpException.notFound("Pihak tidak ditemukan.");
        }
        return p;
    }

    /** POST /parties — port {@code CreatePartySchema}. */
    @ErpTx
    public Party create(JsonNode raw) {
        JsonNode body = AdminZod.body(raw);
        JsonNode name = AdminZod.field(body, "name");
        JsonNode partyType = AdminZod.field(body, "partyType");
        JsonNode phone = AdminZod.field(body, "phone");
        JsonNode email = AdminZod.field(body, "email");
        JsonNode address = AdminZod.field(body, "address");
        JsonNode taxId = AdminZod.field(body, "taxId");
        JsonNode isWapu = AdminZod.field(body, "isWapu");

        if (!name.isTextual()) {
            AdminZod.fail(AdminZod.expected("string", name));
        }
        if (name.asText().isEmpty()) {
            AdminZod.fail("Nama wajib diisi.");
        }
        PartyType type = partyType.isTextual() ? PartyList.parseType(partyType.asText()) : null;
        if (type == null) {
            AdminZod.fail(AdminZod.PARTY_TYPE_MSG);
        }
        AdminZod.fail(AdminZod.optionalNullableString(phone));
        AdminZod.fail(AdminZod.emailOrEmpty(email, "Format email tidak valid."));
        AdminZod.fail(AdminZod.optionalNullableString(address));
        AdminZod.fail(AdminZod.optionalNullableString(taxId));
        AdminZod.fail(AdminZod.optionalBoolean(isWapu));

        Party p = new Party();
        p.setName(name.asText());
        p.setPartyType(type);
        p.setPhone(orNull(AdminZod.text(phone)));
        p.setEmail(orNull(AdminZod.text(email)));
        p.setAddress(orNull(AdminZod.text(address)));
        p.setTaxId(orNull(AdminZod.text(taxId)));
        p.setIsWapu(AdminZod.present(isWapu) && isWapu.asBoolean());
        em.persist(p);
        return reload(p.getId());
    }

    /**
     * PUT /parties/:id — port {@code UpdatePartySchema} (= Create.partial() + isActive). Teks opsional
     * memakai {@code clearable}: tidak dikirim = tetap, null/kosong = dikosongkan, selain itu di-trim.
     */
    @ErpTx
    public Party update(String id, JsonNode raw) {
        JsonNode body = AdminZod.body(raw);
        JsonNode name = AdminZod.field(body, "name");
        JsonNode partyType = AdminZod.field(body, "partyType");
        JsonNode phone = AdminZod.field(body, "phone");
        JsonNode email = AdminZod.field(body, "email");
        JsonNode address = AdminZod.field(body, "address");
        JsonNode taxId = AdminZod.field(body, "taxId");
        JsonNode isWapu = AdminZod.field(body, "isWapu");
        JsonNode isActive = AdminZod.field(body, "isActive");

        if (AdminZod.present(name)) {
            AdminZod.fail(AdminZod.optionalString(name));
            if (name.asText().isEmpty()) {
                AdminZod.fail("Nama wajib diisi.");
            }
        }
        PartyType type = null;
        if (AdminZod.present(partyType)) {
            type = partyType.isTextual() ? PartyList.parseType(partyType.asText()) : null;
            if (type == null) {
                AdminZod.fail(AdminZod.PARTY_TYPE_MSG);
            }
        }
        AdminZod.fail(AdminZod.optionalNullableString(phone));
        AdminZod.fail(AdminZod.emailOrEmpty(email, "Format email tidak valid."));
        AdminZod.fail(AdminZod.optionalNullableString(address));
        AdminZod.fail(AdminZod.optionalNullableString(taxId));
        AdminZod.fail(AdminZod.optionalBoolean(isWapu));
        AdminZod.fail(AdminZod.optionalBoolean(isActive));

        Party p = em.find(Party.class, id);
        if (p == null) {
            throw ErpException.notFound("Data pihak tidak ditemukan.");
        }
        if (AdminZod.present(name)) {
            p.setName(name.asText());
        }
        if (type != null) {
            p.setPartyType(type);
        }
        if (AdminZod.present(phone)) {
            p.setPhone(clearable(phone));
        }
        if (AdminZod.present(email)) {
            p.setEmail(clearable(email));
        }
        if (AdminZod.present(address)) {
            p.setAddress(clearable(address));
        }
        if (AdminZod.present(taxId)) {
            p.setTaxId(clearable(taxId));
        }
        if (AdminZod.present(isActive)) {
            p.setIsActive(isActive.asBoolean());
        }
        // Zod 4: default(false) tetap diterapkan di dalam .partial(), jadi isWapu yang tidak dikirim
        // tertulis false — perilaku sumber dipertahankan (form mitra selalu mengirim isWapu).
        p.setIsWapu(AdminZod.present(isWapu) && isWapu.asBoolean());
        p.setUpdatedAt(Instant.now());
        return reload(id);
    }

    /**
     * DELETE /parties/:id (capability doc.cancel dicek di controller). Mitra yang punya invoice
     * penjualan/pembelian atau pembayaran hanya dinonaktifkan; selainnya dihapus permanen.
     */
    @ErpTx
    public Map<String, Object> delete(String id) {
        long sales = count("select count(i) from SalesInvoice i where i.partyId = :id", id);
        long purchase = count("select count(i) from PurchaseInvoice i where i.partyId = :id", id);
        long payments = count("select count(pm) from Payment pm where pm.partyId = :id", id);
        long linked = sales + purchase + payments;
        Party p = em.find(Party.class, id);
        if (p == null) {
            throw ErpException.notFound("Data mitra tidak ditemukan.");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (linked > 0) {
            p.setIsActive(false);
            p.setUpdatedAt(Instant.now());
            out.put("message", "Mitra dinonaktifkan karena memiliki " + linked + " transaksi terkait.");
            out.put("deactivated", true);
            return out;
        }
        em.remove(p);
        // Flush di sini agar pelanggaran FK (dokumen lain yang merujuk mitra) dipetakan handler ERP.
        em.flush();
        out.put("message", "Mitra dihapus.");
        out.put("deleted", true);
        return out;
    }

    private long count(String jpql, String id) {
        return em.createQuery(jpql, Long.class).setParameter("id", id).getSingleResult();
    }

    private Party reload(String id) {
        em.flush();
        em.clear();
        return em.find(Party.class, id);
    }

    /** JS {@code value || null}. */
    private static String orNull(String v) {
        return v == null || v.isEmpty() ? null : v;
    }

    /** Port {@code utils/clearable.ts} untuk field yang dikirim: null/kosong → null, selain itu trim. */
    private static String clearable(JsonNode v) {
        if (v.isNull()) {
            return null;
        }
        String t = v.asText().strip();
        return t.isEmpty() ? null : t;
    }
}
