package id.neravoxa.erp.eam.asset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import id.neravoxa.erp.core.ErpDates;
import id.neravoxa.erp.core.ErpException;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.ErpQuery;
import id.neravoxa.erp.core.ErpTx;
import id.neravoxa.erp.core.ListQuery;
import id.neravoxa.erp.core.Money;
import id.neravoxa.erp.core.accounting.ErpFiscalYearService;
import id.neravoxa.erp.core.accounting.ErpGlPostingService;
import id.neravoxa.erp.entity.DepreciationEntry;
import id.neravoxa.erp.entity.FiscalYear;
import id.neravoxa.erp.entity.FixedAsset;
import id.neravoxa.erp.entity.JournalEntry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Aset tetap (register, jadwal & posting penyusutan, pelepasan, impor) — port {@code routes/fixedAssets.ts}.
 *
 * <p>Aturan GL penyusutan (sama dengan sumber): per entri jadwal satu jurnal {@code DEP-NNNNNN} (nomor =
 * jumlah jurnal + 1) berstatus Submitted bertanggal periode, Dr akun beban penyusutan aset / Cr akun
 * akumulasi penyusutan aset, narasi {@code "Depresiasi <nama> - YYYY-MM"}; lalu akumulasi & nilai buku aset
 * diperbarui. Pelepasan aset di sumber HANYA menandai status Disposed (tanpa jurnal) — aset tidak lagi
 * disusutkan oleh run bulanan; nominal tidak bisa diedit (koreksi = lepas + buat aset baru).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpFixedAssetService {

    /** decimal.js default: presisi 20 digit signifikan, ROUND_HALF_UP. */
    private static final MathContext DECIMAL_JS = new MathContext(20, RoundingMode.HALF_UP);
    private static final Pattern MONTH = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final String FETCH_ACCOUNTS = "left join fetch f.assetAccount left join fetch f.depreciationAccount "
            + "left join fetch f.accumulatedDepAccount";

    private final ErpGlPostingService gl;
    private final ErpFiscalYearService fiscalYears;

    @PersistenceContext(unitName = "erp")
    private EntityManager em;

    // ───────────────────────────── JSON ─────────────────────────────

    private static void putAccounts(ObjectNode o, FixedAsset f) {
        o.set("assetAccount", GaJs.pick(f.getAssetAccount(), "id", "accountNumber", "name"));
        o.set("depreciationAccount", GaJs.pick(f.getDepreciationAccount(), "id", "accountNumber", "name"));
        o.set("accumulatedDepAccount", GaJs.pick(f.getAccumulatedDepAccount(), "id", "accountNumber", "name"));
    }

    /** Baris aset + include akun (selalu), user {fullName}, gaAsset {id,code,name} sesuai endpoint. */
    private static ObjectNode json(FixedAsset f, boolean withUser, boolean withGa, List<DepreciationEntry> entries) {
        ObjectNode o = GaJs.scalars(f);
        putAccounts(o, f);
        if (entries != null) {
            ArrayNode arr = ErpJson.arrayNode();
            entries.forEach(e -> arr.add(GaJs.scalars(e)));
            o.set("depreciationEntries", arr);
        }
        if (withUser) {
            o.set("user", GaJs.pick(f.getUser(), "fullName"));
        }
        if (withGa) {
            o.set("gaAsset", GaJs.pick(f.getGaAsset(), "id", "code", "name"));
        }
        return o;
    }

    private FixedAsset load(String id) {
        List<FixedAsset> rows = em.createQuery("select f from FixedAsset f " + FETCH_ACCOUNTS
                        + " left join fetch f.user left join fetch f.gaAsset where f.id = :id", FixedAsset.class)
                .setParameter("id", id).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ───────────────────────────── GET / ─────────────────────────────

    /**
     * Tanpa {@code page}: array semua aset (status/kategori persis), terbaru dulu. Dengan {@code page}: page,
     * limit (≤500), search, status, category, startDate/endDate, sort → {@code { data, total, page, limit }}.
     */
    @ErpTx(readOnly = true)
    public Object list(Map<String, String> query) {
        if (query.containsKey("page")) {
            ErpQuery<FixedAsset> q = FixedAssetList.buildWhere(ErpQuery.of(em, FixedAsset.class, "f"), query)
                    .fetch(FETCH_ACCOUNTS, "left join fetch f.user", "left join fetch f.gaAsset");
            ListQuery.Paging paging = ListQuery.paging(query, 25, 500);
            ListQuery.Sort sort = ListQuery.sort(query.get("sort"), FixedAssetList.SORT_FIELDS, FixedAssetList.DEFAULT_SORT);
            q.orderBy(FixedAssetList.orderBy(sort));
            List<JsonNode> data = new ArrayList<>();
            q.list(paging.skip(), paging.limit()).forEach(f -> data.add(json(f, true, true, null)));
            return ListQuery.result(data, q.count(), paging);
        }
        ErpQuery<FixedAsset> q = ErpQuery.of(em, FixedAsset.class, "f")
                .fetch(FETCH_ACCOUNTS, "left join fetch f.user")
                .whereIf(GaJs.truthy(query.get("status")), "f.status = :status", "status", query.get("status"))
                .whereIf(GaJs.truthy(query.get("category")), "f.category = :category", "category", query.get("category"))
                .orderBy("f.createdAt desc");
        ArrayNode out = ErpJson.arrayNode();
        q.list().forEach(f -> out.add(json(f, true, false, null)));
        return out;
    }

    @ErpTx(readOnly = true)
    public List<String> categories() {
        return em.createQuery("select distinct f.category from FixedAsset f order by f.category asc", String.class)
                .getResultList();
    }

    @ErpTx(readOnly = true)
    public ObjectNode detail(String id) {
        FixedAsset f = load(id);
        if (f == null) {
            throw ErpException.notFound("Aset tidak ditemukan.");
        }
        List<DepreciationEntry> entries = em.createQuery(
                        "select e from DepreciationEntry e where e.fixedAssetId = :id order by e.periodDate asc",
                        DepreciationEntry.class)
                .setParameter("id", id).getResultList();
        return json(f, true, true, entries);
    }

    // ───────────────────────────── POST / & /import ─────────────────────────────

    /** Satu baris aset baru (POST / atau satu baris impor CSV). */
    record AssetRow(String name, String category, String description, String acquisitionDate,
                    BigDecimal acquisitionCost, int usefulLifeMonths, BigDecimal salvageValue,
                    String depreciationMethod, String assetAccountId, String depreciationAccountId,
                    String accumulatedDepAccountId, boolean gaPresent, String gaAssetId) {
    }

    /** {@code CreateFixedAssetSchema} (withMessages=true) atau elemen {@code BulkCreateAssetSchema} (pesan default). */
    static AssetRow parseRow(GaZod z, boolean create) {
        String name = z.requiredString("name", 1, create ? "Nama aset wajib diisi." : null, 200);
        String category = z.requiredString("category", 1, create ? "Kategori wajib diisi." : null, 100);
        String description = z.optString("description", 500);
        String acquisitionDate = z.requiredString("acquisitionDate", 1, create ? "Tanggal akuisisi wajib diisi." : null, null);
        BigDecimal cost = z.coerce("acquisitionCost", null, false, BigDecimal.ZERO, true,
                create ? "Harga perolehan harus lebih dari 0." : null, null, null);
        BigDecimal life = z.coerce("usefulLifeMonths", null, true, BigDecimal.ZERO, true,
                create ? "Umur ekonomis (bulan) harus lebih dari 0." : null, BigDecimal.valueOf(1200), null);
        BigDecimal salvage = z.coerce("salvageValue", BigDecimal.ZERO, false, BigDecimal.ZERO, false, null, null, null);
        String method = create ? z.enumValue("depreciationMethod", "straight_line", true, false, "straight_line")
                : "straight_line";
        String assetAcc = z.uuid("assetAccountId", create ? "assetAccountId harus UUID valid." : null);
        String depAcc = z.uuid("depreciationAccountId", create ? "depreciationAccountId harus UUID valid." : null);
        String accumAcc = z.uuid("accumulatedDepAccountId", create ? "accumulatedDepAccountId harus UUID valid." : null);
        String gaAssetId = create ? z.optTrimmedString("gaAssetId", 100) : null;
        return new AssetRow(name, category, description, acquisitionDate, cost, life == null ? 0 : life.intValue(),
                salvage, method, assetAcc, depAcc, accumAcc, create && z.has("gaAssetId"), gaAssetId);
    }

    private long assetCount() {
        return em.createQuery("select count(f) from FixedAsset f", Long.class).getSingleResult();
    }

    private static String assetNumber(long n) {
        return "FA-" + String.format("%04d", n);
    }

    /** Simpan aset + jadwal penyusutan garis lurus; mengembalikan aset & jumlah entri jadwal. */
    private int persistWithSchedule(FixedAsset f, AssetRow row) {
        BigDecimal cost = row.acquisitionCost();
        BigDecimal salvage = Money.nz(row.salvageValue());
        f.setName(row.name());
        f.setCategory(row.category());
        f.setDescription(row.description());
        f.setAcquisitionDate(ErpDates.toInstant(row.acquisitionDate()));
        f.setAcquisitionCost(cost);
        f.setUsefulLifeMonths(row.usefulLifeMonths());
        f.setSalvageValue(salvage);
        f.setDepreciationMethod(row.depreciationMethod() == null ? "straight_line" : row.depreciationMethod());
        f.setBookValue(cost);
        f.setAssetAccountId(row.assetAccountId());
        f.setDepreciationAccountId(row.depreciationAccountId());
        f.setAccumulatedDepAccountId(row.accumulatedDepAccountId());
        em.persist(f);
        em.flush();

        // Jadwal: tanggal 1 tiap bulan setelah perolehan; entri terakhir menyerap sisa pembulatan.
        int n = row.usefulLifeMonths();
        BigDecimal base = cost.subtract(salvage);
        BigDecimal monthly = base.divide(BigDecimal.valueOf(n), DECIMAL_JS);
        for (int i = 1; i <= n; i++) {
            DepreciationEntry e = new DepreciationEntry();
            e.setFixedAssetId(f.getId());
            e.setPeriodDate(GaJs.schedulePeriod(f.getAcquisitionDate(), i));
            e.setAmount(i == n
                    ? Money.round2(base.subtract(monthly.multiply(BigDecimal.valueOf(n - 1L), DECIMAL_JS)))
                    : Money.round2(monthly));
            e.setIsPosted(false);
            em.persist(e);
        }
        em.flush();
        return n;
    }

    @ErpTx
    public ObjectNode create(AssetRow row, String userId) {
        FixedAssetGaLink.Link link = FixedAssetGaLink.resolve(em, row.gaPresent(), row.gaAssetId(), null);
        Instant acq = ErpDates.toInstant(row.acquisitionDate());
        FiscalYear fy = fiscalYears.findContaining(acq).orElseThrow(() ->
                ErpException.business("Tahun buku tidak ditemukan untuk tanggal akuisisi."));
        FixedAsset f = new FixedAsset();
        f.setAssetNumber(assetNumber(assetCount() + 1));
        f.setGaAssetId(link.gaAssetId());
        f.setFiscalYearId(fy.getId());
        f.setCreatedBy(userId);
        persistWithSchedule(f, row);
        String id = f.getId();
        em.clear();
        return json(load(id), false, true, null);
    }

    /**
     * Impor CSV: buat aset + jadwal, TANPA jurnal saldo awal (dicatat manual lewat jurnal). Tahun buku per
     * tanggal perolehan, fallback ke tahun buku terbaru.
     */
    @ErpTx
    public Map<String, Object> importAssets(List<AssetRow> rows, String userId) {
        List<FiscalYear> fys = em.createQuery("select f from FiscalYear f order by f.startDate desc", FiscalYear.class)
                .getResultList();
        if (fys.isEmpty()) {
            throw ErpException.business("Tidak ada Tahun Buku.");
        }
        long count = assetCount();
        int scheduleEntries = 0;
        List<Map<String, Object>> created = new ArrayList<>();
        for (AssetRow row : rows) {
            Instant acq = ErpDates.toInstant(row.acquisitionDate());
            FiscalYear fy = fys.stream()
                    .filter(y -> !y.getStartDate().isAfter(acq) && !acq.isAfter(y.getEndDate()))
                    .findFirst().orElse(fys.get(0));
            count++;
            FixedAsset f = new FixedAsset();
            f.setAssetNumber(assetNumber(count));
            f.setFiscalYearId(fy.getId());
            f.setCreatedBy(userId);
            scheduleEntries += persistWithSchedule(f, row);
            created.add(ErpJson.map("assetNumber", f.getAssetNumber(), "name", row.name()));
        }
        log.info("Fixed assets imported count={} scheduleEntries={} userId={}", created.size(), scheduleEntries, userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("count", created.size());
        out.put("scheduleEntries", scheduleEntries);
        out.put("created", created);
        return out;
    }

    // ───────────────────────────── PUT /:id ─────────────────────────────

    /** Hanya field "soft" (label/metadata + tautan EAM); nominal & akun sengaja tidak bisa diubah. */
    @ErpTx
    public ObjectNode update(String id, JsonNode body) {
        GaZod z = GaZod.of(body, true);
        String name = z.requiredString("name", 1, null, 200);
        String category = z.requiredString("category", 1, null, 100);
        String description = z.optString("description", 500);
        String gaAssetId = z.optTrimmedString("gaAssetId", 100);
        z.throwIfInvalid();
        FixedAsset f = em.find(FixedAsset.class, id);
        if (f == null) {
            throw ErpException.notFound("Aset tidak ditemukan.");
        }
        FixedAssetGaLink.Link link = FixedAssetGaLink.resolve(em, z.has("gaAssetId"), gaAssetId, id);
        if (name != null) {
            f.setName(name);
        }
        if (category != null) {
            f.setCategory(category);
        }
        if (z.has("description")) {
            f.setDescription(description);
        }
        if (link.present()) {
            f.setGaAssetId(link.gaAssetId());
        }
        em.flush();
        em.clear();
        return json(load(id), true, true, null);
    }

    // ───────────────────────────── penyusutan ─────────────────────────────

    private long journalCount() {
        return em.createQuery("select count(j) from JournalEntry j", Long.class).getSingleResult();
    }

    private static String depNumber(long n) {
        return "DEP-" + String.format("%06d", n);
    }

    /**
     * Klaim atomik entri (isPosted false → true) agar posting idempoten walau ada request paralel.
     * True bila entri ini berhasil diklaim oleh transaksi ini.
     */
    private boolean claim(String entryId) {
        return em.createQuery("update DepreciationEntry e set e.isPosted = true where e.id = :id and e.isPosted = false")
                .setParameter("id", entryId).executeUpdate() == 1;
    }

    /**
     * Jurnal penyusutan satu entri + tandai entri + perbarui akumulasi/nilai buku aset (atomik di SQL agar
     * dua entri satu aset dalam satu run tidak saling menimpa). Entri bernilai 0 (harga = residu) hanya
     * ditandai terposting tanpa jurnal karena jurnal tanpa baris bernilai ditolak kernel GL.
     */
    private JournalEntry postEntry(DepreciationEntry entry, FixedAsset asset, String entryNumber, String fiscalYearId,
                                   String userId) {
        BigDecimal amount = Money.nz(entry.getAmount());
        JournalEntry je = null;
        if (amount.signum() != 0) {
            je = gl.journal()
                    .number(entryNumber)
                    .date(entry.getPeriodDate())
                    .narration("Depresiasi " + asset.getName() + " - " + ErpDates.isoDay(entry.getPeriodDate()).substring(0, 7))
                    .fiscalYear(fiscalYearId)
                    .createdBy(userId)
                    .line(asset.getDepreciationAccountId(), amount, BigDecimal.ZERO, "Beban depresiasi " + asset.getName())
                    .line(asset.getAccumulatedDepAccountId(), BigDecimal.ZERO, amount, "Akm. depresiasi " + asset.getName())
                    .post();
            em.createQuery("update DepreciationEntry e set e.journalEntryId = :je where e.id = :id")
                    .setParameter("je", je.getId()).setParameter("id", entry.getId()).executeUpdate();
        }
        em.createQuery("update FixedAsset f set f.accumulatedDepreciation = f.accumulatedDepreciation + :amt, "
                        + "f.bookValue = f.acquisitionCost - (f.accumulatedDepreciation + :amt), f.updatedAt = :now "
                        + "where f.id = :id")
                .setParameter("amt", amount).setParameter("now", Instant.now()).setParameter("id", asset.getId())
                .executeUpdate();
        return je;
    }

    /** POST /:id/depreciate — posting satu periode jadwal ({@code entryId}). */
    @ErpTx
    public Map<String, Object> depreciate(String entryId, String userId) {
        List<DepreciationEntry> rows = em.createQuery("select e from DepreciationEntry e join fetch e.fixedAsset "
                        + "where e.id = :id", DepreciationEntry.class)
                .setParameter("id", entryId).getResultList();
        if (rows.isEmpty()) {
            throw ErpException.notFound("Jadwal depresiasi tidak ditemukan.");
        }
        DepreciationEntry entry = rows.get(0);
        if (Boolean.TRUE.equals(entry.getIsPosted())) {
            throw ErpException.business("Depresiasi sudah diposting.");
        }
        FiscalYear fy = fiscalYears.findContaining(entry.getPeriodDate()).orElse(null);
        if (fy == null) {
            log.error("POST /fixed-assets/:id/depreciate: Tahun buku tidak ditemukan untuk {}", entry.getPeriodDate());
            throw ErpException.of(500, "Gagal posting depresiasi.");
        }
        if (!claim(entry.getId())) {
            throw ErpException.business("Depresiasi sudah diposting.");
        }
        JournalEntry je = postEntry(entry, entry.getFixedAsset(), depNumber(journalCount() + 1), fy.getId(), userId);
        return ErpJson.map("success", true, "journalEntryId", je == null ? null : je.getId());
    }

    /** Bulan "YYYY-MM" (atau bulan berjalan UTC bila kosong). */
    private static YearMonth month(String raw) {
        if (raw == null) {
            return YearMonth.now(ZoneOffset.UTC);
        }
        String[] p = raw.split("-");
        return YearMonth.of(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
    }

    /**
     * POST /depreciate-bulk — posting semua entri belum terposting pada bulan itu untuk aset Active; idempoten
     * per entri (yang sudah terposting dilewati). {@code dryRun} hanya pratinjau.
     */
    @ErpTx
    public Map<String, Object> depreciateBulk(JsonNode body, String userId) {
        GaZod z = GaZod.of(body, false);
        String monthRaw = z.optionalString("month");
        if (monthRaw != null && !MONTH.matcher(monthRaw).matches()) {
            z.issue("Format month harus 'YYYY-MM'.");
        }
        Boolean dryRun = z.bool("dryRun", false);
        z.throwIfInvalid();

        YearMonth ym = month(monthRaw);
        String label = String.format("%04d-%02d", ym.getYear(), ym.getMonthValue());
        Instant periodStart = ym.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant periodEnd = ym.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);

        List<DepreciationEntry> due = em.createQuery("select e from DepreciationEntry e join fetch e.fixedAsset f "
                        + "where e.periodDate >= :s and e.periodDate <= :e and e.isPosted = false and f.status = 'Active' "
                        + "order by e.periodDate asc, f.assetNumber asc", DepreciationEntry.class)
                .setParameter("s", periodStart).setParameter("e", periodEnd).getResultList();

        Map<String, Object> out = new LinkedHashMap<>();
        if (due.isEmpty()) {
            out.put("posted", 0);
            out.put("totalAmount", 0);
            out.put("jeNumbers", List.of());
            out.put("skipped", 0);
            out.put("message", "Tidak ada jadwal depresiasi belum ter-post di periode " + label + ".");
            return out;
        }
        BigDecimal totalDue = Money.sum(due, DepreciationEntry::getAmount);
        if (Boolean.TRUE.equals(dryRun)) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (DepreciationEntry e : due) {
                FixedAsset a = e.getFixedAsset();
                items.add(ErpJson.map("assetNumber", a.getAssetNumber(), "name", a.getName(),
                        "category", a.getCategory(), "amount", e.getAmount(), "periodDate", e.getPeriodDate()));
            }
            out.put("posted", 0);
            out.put("totalAmount", totalDue);
            out.put("jeNumbers", List.of());
            out.put("skipped", 0);
            out.put("dryRun", true);
            out.put("items", items);
            return out;
        }

        // Tahun buku yang mencakup bulan ini. Akhir tahun buku disimpan tengah malam (mis. 31 Des 00:00), jadi
        // dibandingkan dengan AWAL hari terakhir bulan agar run Desember tidak gagal.
        Instant lastDayStart = ym.atEndOfMonth().atStartOfDay(ZoneOffset.UTC).toInstant();
        List<FiscalYear> fy = em.createQuery("select f from FiscalYear f where f.startDate <= :s and f.endDate >= :e "
                        + "order by f.startDate", FiscalYear.class)
                .setParameter("s", periodStart).setParameter("e", lastDayStart).setMaxResults(1).getResultList();
        if (fy.isEmpty()) {
            throw ErpException.business("Tahun buku untuk " + label + " tidak ditemukan.");
        }

        long next = journalCount();
        List<String> jeNumbers = new ArrayList<>();
        int posted = 0;
        int skipped = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (DepreciationEntry e : due) {
            if (!claim(e.getId())) {
                skipped++; // sudah diposting request lain sejak dibaca
                continue;
            }
            String number = depNumber(++next);
            JournalEntry je = postEntry(e, e.getFixedAsset(), number, fy.get(0).getId(), userId);
            if (je != null) {
                jeNumbers.add(number);
            } else {
                next--;
            }
            posted++;
            total = total.add(Money.nz(e.getAmount()));
        }
        log.info("Bulk depreciation posted month={} posted={} totalAmount={} userId={}", label, posted, total, userId);
        out.put("posted", posted);
        out.put("totalAmount", total);
        out.put("jeNumbers", jeNumbers);
        out.put("skipped", skipped);
        out.put("message", "Berhasil post " + posted + " entry depresiasi (" + posted + " jurnal) untuk periode " + label + ".");
        return out;
    }

    // ───────────────────────────── pelepasan ─────────────────────────────

    /**
     * POST /:id/dispose — tandai aset Disposed (tanggal & nilai pelepasan). Sama dengan sumber: TIDAK ada
     * jurnal laba/rugi pelepasan; aset tidak lagi ikut run penyusutan bulanan.
     */
    @ErpTx
    public Map<String, Object> dispose(String id, JsonNode body) {
        GaZod z = GaZod.of(body, false);
        String disposalDate = z.requiredString("disposalDate", 1, "Tanggal pelepasan wajib diisi.", null);
        BigDecimal disposalAmount = z.coerce("disposalAmount", BigDecimal.ZERO, false, BigDecimal.ZERO, false,
                null, null, null);
        z.throwIfInvalid();
        FixedAsset f = em.find(FixedAsset.class, id);
        if (f == null) {
            throw ErpException.notFound("Aset tidak ditemukan.");
        }
        Instant date = ErpDates.toInstant(disposalDate);
        int n = em.createQuery("update FixedAsset f set f.status = 'Disposed', f.disposalDate = :d, "
                        + "f.disposalAmount = :amt, f.updatedAt = :now where f.id = :id and f.status = 'Active'")
                .setParameter("d", date).setParameter("amt", Money.nz(disposalAmount))
                .setParameter("now", Instant.now()).setParameter("id", id).executeUpdate();
        if (n == 0) {
            throw ErpException.business("Aset sudah tidak aktif.");
        }
        return ErpJson.map("success", true);
    }

    // ───────────────────────────── ringkasan ─────────────────────────────

    /** GET /summary/by-category — aset Active per kategori. */
    @ErpTx(readOnly = true)
    public List<Map<String, Object>> summaryByCategory() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT category, COUNT(*) AS count, SUM(acquisition_cost) AS total_cost, "
                + "SUM(accumulated_depreciation) AS total_accumulated, SUM(book_value) AS total_book_value "
                + "FROM fixed_assets WHERE status = 'Active' GROUP BY category ORDER BY category").getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(ErpJson.map("category", r[0],
                    "count", ((Number) r[1]).longValue(),
                    "total_cost", Money.of(r[2]),
                    "total_accumulated", Money.of(r[3]),
                    "total_book_value", Money.of(r[4])));
        }
        return out;
    }
}
