package id.enterpriseapps.erp.cashbook;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Draft Buku Kas — aturan murni (tanpa DB), port {@code utils/cashBookRules.ts}. Arah kas dan DR/CR
 * setiap jurnal diputuskan DI SINI secara deterministik; usulan engine/manusia hanya
 * {txnType, akun, confidence, reason} per baris dan tidak pernah dipercaya untuk arah akuntansi.
 *
 * <pre>
 *   txnType         arah    jurnal
 *   pengeluaran     keluar  DR akun lawan (beban)      / CR kas
 *   pemasukan       masuk   DR kas                     / CR akun lawan (sumber)
 *   uang_muka       keluar  DR Uang Muka Karyawan      / CR kas
 *   terima_kasbon   masuk   DR kas                     / CR Uang Muka Karyawan
 *   lainnya         keluar  DR akun usulan / CR kas  (selalu needsReview)
 *   lainnya         masuk   DR kas / CR akun usulan
 * </pre>
 */
final class CashBookRules {

    static final List<String> TXN_TYPES = List.of("pengeluaran", "pemasukan", "uang_muka", "terima_kasbon", "lainnya");
    static final List<String> DIRECTIONS = List.of("masuk", "keluar");

    /** Batas keras baris per draft (buat + tambah). */
    static final int ROW_LIMIT = 300;
    /** Baris dengan keyakinan di bawah ini ditandai perlu ditinjau. */
    static final double REVIEW_THRESHOLD = 0.7;
    static final int MAX_DESCRIPTION = 500;
    private static final int MAX_REASON = 500;
    private static final double MIN_JACCARD = 0.6;
    private static final BigDecimal TOO_BIG = new BigDecimal("1e18");

    private static final Pattern RP_PREFIX = Pattern.compile("^(rp\\.?|idr)" + JsCompat.WS + "*", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO_LIKE = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[T ].*)?$");
    private static final Pattern DMY = Pattern.compile("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$");
    private static final Pattern DIACRITICS = Pattern.compile("[\\u0300-\\u036f]");

    private CashBookRules() {
    }

    static boolean isTxnType(Object v) {
        return v instanceof String s && TXN_TYPES.contains(s);
    }

    static boolean isDirection(Object v) {
        return "masuk".equals(v) || "keluar".equals(v);
    }

    static boolean isAdvanceType(String t) {
        return "uang_muka".equals(t) || "terima_kasbon".equals(t);
    }

    /** Arah yang tersirat dari tiap jenis; {@code lainnya} cocok untuk keduanya. */
    static boolean txnTypeFits(String type, String direction) {
        if ("lainnya".equals(type)) {
            return true;
        }
        if ("pengeluaran".equals(type) || "uang_muka".equals(type)) {
            return "keluar".equals(direction);
        }
        return "masuk".equals(direction);
    }

    static String defaultTxnType(String direction) {
        return "masuk".equals(direction) ? "pemasukan" : "pengeluaran";
    }

    /** Jenis yang bertentangan dengan arah dipaksa menjadi jenis polos arah sebenarnya. */
    static String coerceTxnType(String type, String direction) {
        return txnTypeFits(type, direction) ? type : defaultTxnType(direction);
    }

    // ───────────────────────────── uang / tanggal / teks ─────────────────────────────

    static BigDecimal roundMoney(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Nominal angka atau teks ("Rp 1.250.000,50", "1,250,000.50", "(500)"). Absen/null → 0;
     * tak terbaca → null; hasil dibulatkan 2 desimal (half up). {@code v} = node JSON (null = undefined).
     */
    static BigDecimal parseMoney(JsonNode v) {
        if (v == null || v.isMissingNode() || v.isNull()) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (v.isNumber()) {
            double d = v.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return null;
            }
            return roundMoney(new BigDecimal(JsCompat.numberToString(d).replace("e+", "e")));
        }
        if (!v.isTextual()) {
            return null;
        }
        return parseMoneyText(v.textValue());
    }

    static BigDecimal parseMoneyText(String raw) {
        String s = JsCompat.trim(raw);
        s = RP_PREFIX.matcher(s).replaceFirst("");
        s = s.replaceAll(JsCompat.WS + "+", "");
        if (s.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        boolean negative = false;
        if (s.length() >= 2 && s.startsWith("(") && s.endsWith(")")) {
            negative = true;
            s = s.substring(1, s.length() - 1);
        }
        if (s.startsWith("-")) {
            negative = !negative;
            s = s.substring(1);
        }
        if (!s.matches("^[0-9.,]+$") || !s.matches(".*[0-9].*")) {
            return null;
        }
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        String normalized;
        if (lastDot >= 0 && lastComma >= 0) {
            char decimalSep = lastDot > lastComma ? '.' : ',';
            String thousandSep = decimalSep == '.' ? "," : ".";
            String joined = String.join("", s.split(Pattern.quote(thousandSep), -1));
            int at = joined.indexOf(decimalSep);
            normalized = at < 0 ? joined : joined.substring(0, at) + "." + joined.substring(at + 1);
        } else if (lastDot >= 0 || lastComma >= 0) {
            String sep = lastDot >= 0 ? "." : ",";
            String[] parts = s.split(Pattern.quote(sep), -1);
            String tail = parts[parts.length - 1];
            boolean isThousands = parts.length > 2
                    || (tail.length() == 3 && !parts[0].isEmpty() && parts[0].length() <= 3 && !parts[0].equals("0"));
            normalized = isThousands ? String.join("", parts) : parts[0] + "." + tail;
        } else {
            normalized = s;
        }
        if (normalized.chars().filter(c -> c == '.').count() > 1) {
            return null;
        }
        try {
            BigDecimal d = roundMoney(new BigDecimal(normalized));
            return negative ? d.negate() : d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** YYYY-MM-DD, DD/MM/YYYY, DD-MM-YYYY (juga DD.MM.YYYY) → YYYY-MM-DD tervalidasi, atau null. */
    static String normalizeDateInput(JsonNode v) {
        if (v == null || !v.isTextual()) {
            return null;
        }
        return normalizeDateInput(v.textValue());
    }

    static String normalizeDateInput(String raw) {
        if (raw == null) {
            return null;
        }
        String s = JsCompat.trim(raw);
        int y;
        int m;
        int d;
        Matcher match = ISO_LIKE.matcher(s);
        if (match.matches()) {
            y = Integer.parseInt(match.group(1));
            m = Integer.parseInt(match.group(2));
            d = Integer.parseInt(match.group(3));
        } else {
            match = DMY.matcher(s);
            if (!match.matches()) {
                return null;
            }
            d = Integer.parseInt(match.group(1));
            m = Integer.parseInt(match.group(2));
            y = Integer.parseInt(match.group(3));
        }
        // Date.UTC memetakan tahun 0–99 ke 1900+; hasilnya tidak lolos cek bolak-balik di sumber.
        if (y < 100) {
            return null;
        }
        try {
            LocalDate.of(y, m, d);
        } catch (DateTimeException e) {
            return null;
        }
        return String.format("%04d-%02d-%02d", y, m, d);
    }

    static String collapseSpaces(String s) {
        return JsCompat.collapseSpaces(s);
    }

    private static String stripDiacritics(String s) {
        return DIACRITICS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
    }

    /**
     * Pola untuk peta terpelajari: huruf kecil, tanpa diakritik/tanda baca, angka & token satu huruf
     * dibuang (jadi "Beli ATK 2 box" ≈ "beli atk box").
     */
    static String descriptionPattern(String description) {
        String[] tokens = stripDiacritics(description.toLowerCase(Locale.ROOT)).replaceAll("[^a-z0-9]+", " ").split(" ", -1);
        List<String> kept = new ArrayList<>();
        for (String t : tokens) {
            if (t.length() >= 2 && !t.matches("^\\d+$")) {
                kept.add(t);
            }
        }
        String joined = String.join(" ", kept.subList(0, Math.min(12, kept.size())));
        return JsCompat.slice(joined, 120);
    }

    /** Kunci ketat deteksi duplikat (angka dipertahankan). */
    static String duplicateKey(String date, BigDecimal amount, String direction, String description) {
        String norm = stripDiacritics(description.toLowerCase(Locale.ROOT)).replaceAll("[^a-z0-9]+", "");
        return date + "|" + roundMoney(amount).toPlainString() + "|" + direction + "|" + norm;
    }

    static String mergeReasons(List<String> parts) {
        Set<String> seen = new LinkedHashSet<>();
        for (String p : parts) {
            String t = p != null ? collapseSpaces(p) : "";
            if (!t.isEmpty()) {
                seen.add(t);
            }
        }
        if (seen.isEmpty()) {
            return null;
        }
        String joined = String.join("; ", seen);
        return joined.length() > MAX_REASON ? joined.substring(0, MAX_REASON - 1) + "…" : joined;
    }

    /** Keyakinan 0..1 dibulatkan 3 desimal; bukan angka → 0. */
    static double clampConfidence(JsonNode v) {
        double n;
        if (v != null && v.isNumber()) {
            n = v.doubleValue();
        } else if (v != null && v.isTextual() && !JsCompat.trim(v.textValue()).isEmpty()) {
            n = JsCompat.number(v.textValue());
        } else {
            n = Double.NaN;
        }
        if (Double.isNaN(n) || Double.isInfinite(n)) {
            return 0;
        }
        return Math.round(Math.min(1, Math.max(0, n)) * 1000) / 1000.0;
    }

    // ───────────────────────────── input baris ─────────────────────────────

    record NormalizedRow(int idx, String date, String description, BigDecimal amount, String direction) {
    }

    /** Hasil ok (value) atau error (pesan). */
    record RowResult<T>(T value, String error) {
        static <T> RowResult<T> ok(T v) {
            return new RowResult<>(v, null);
        }

        static <T> RowResult<T> fail(String e) {
            return new RowResult<>(null, e);
        }

        boolean isOk() {
            return error == null;
        }
    }

    private static JsonNode field(JsonNode raw, String name) {
        return raw == null ? null : raw.get(name);
    }

    private static boolean present(JsonNode n) {
        return n != null && !n.isMissingNode();
    }

    /**
     * Normalisasi satu baris ({date?, description, masuk?|keluar?} atau {amount, direction}).
     * {@code idx} berbasis 1 dan hanya untuk teks error. Tepat satu arah dengan nominal &gt; 0.
     */
    static RowResult<NormalizedRow> normalizeRowInput(JsonNode raw, int idx, String fallbackDate) {
        String at = "Baris " + idx;
        JsonNode descNode = field(raw, "description");
        String description = descNode != null && descNode.isTextual() ? collapseSpaces(descNode.textValue()) : "";
        if (description.isEmpty()) {
            return RowResult.fail(at + ": keterangan wajib diisi.");
        }
        if (description.length() > MAX_DESCRIPTION) {
            return RowResult.fail(at + ": keterangan maksimal " + MAX_DESCRIPTION + " karakter.");
        }

        String date = fallbackDate;
        JsonNode rawDate = field(raw, "date");
        if (present(rawDate) && !rawDate.isNull() && !(rawDate.isTextual() && rawDate.textValue().isEmpty())) {
            String d = normalizeDateInput(rawDate);
            if (d == null) {
                return RowResult.fail(at + ": tanggal \"" + JsCompat.string(rawDate) + "\" tidak valid (format YYYY-MM-DD).");
            }
            date = d;
        }

        BigDecimal amount;
        String direction;
        JsonNode rawMasuk = field(raw, "masuk");
        JsonNode rawKeluar = field(raw, "keluar");
        boolean hasPair = present(rawMasuk) || present(rawKeluar);
        if (hasPair) {
            BigDecimal masuk = parseMoney(rawMasuk);
            BigDecimal keluar = parseMoney(rawKeluar);
            if (masuk == null || keluar == null) {
                return RowResult.fail(at + ": nominal masuk/keluar bukan angka yang valid.");
            }
            if (masuk.signum() < 0 || keluar.signum() < 0) {
                return RowResult.fail(at + ": nominal tidak boleh negatif.");
            }
            boolean hasIn = masuk.signum() > 0;
            boolean hasOut = keluar.signum() > 0;
            if (hasIn && hasOut) {
                return RowResult.fail(at + ": isi salah satu saja, masuk atau keluar (bukan keduanya).");
            }
            if (!hasIn && !hasOut) {
                return RowResult.fail(at + ": nominal masuk atau keluar harus lebih dari 0.");
            }
            amount = hasIn ? masuk : keluar;
            direction = hasIn ? "masuk" : "keluar";
        } else {
            JsonNode dir = field(raw, "direction");
            String dirText = dir != null && dir.isTextual() ? dir.textValue() : null;
            if (!isDirection(dirText)) {
                return RowResult.fail(at + ": arah harus \"masuk\" atau \"keluar\".");
            }
            BigDecimal a = parseMoney(field(raw, "amount"));
            if (a == null) {
                return RowResult.fail(at + ": nominal bukan angka yang valid.");
            }
            if (a.signum() <= 0) {
                return RowResult.fail(at + ": nominal harus lebih dari 0.");
            }
            amount = a;
            direction = dirText;
        }
        if (amount.compareTo(TOO_BIG) >= 0) {
            return RowResult.fail(at + ": nominal terlalu besar.");
        }
        return RowResult.ok(new NormalizedRow(idx, date, description, amount, direction));
    }

    /** Penjaga jumlah baris untuk buat (existing = 0) dan tambah. Null bila lolos. */
    static String checkRowCount(long existing, int adding) {
        if (adding < 1) {
            return "Minimal satu baris diperlukan.";
        }
        if (existing + adding > ROW_LIMIT) {
            return "Maksimal " + ROW_LIMIT + " baris per draft (saat ini " + existing + ", menambah " + adding
                    + "). Pecah menjadi beberapa draft.";
        }
        return null;
    }

    // ───────────────────────────── peta terpelajari ─────────────────────────────

    record LearnedMapEntry(String pattern, String txnType, String accountId, int hits) {
    }

    private static Set<String> tokensOf(String pattern) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : pattern.split(" ", -1)) {
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** Keyakinan untuk akun yang diusulkan peta terpelajari (1 kali tetap "ragu"). */
    static double learnedConfidence(int hits) {
        if (hits >= 3) {
            return 0.85;
        }
        if (hits == 2) {
            return 0.75;
        }
        return 0.65;
    }

    /** Peta terbaik: pola persis dulu, selain itu Jaccard token ≥ 0,6. Jenisnya harus cocok dengan arah. */
    static LearnedMapEntry matchLearned(String description, String direction, List<LearnedMapEntry> maps) {
        String pattern = descriptionPattern(description);
        if (pattern.isEmpty()) {
            return null;
        }
        Set<String> mine = tokensOf(pattern);
        LearnedMapEntry best = null;
        double bestScore = 0;
        for (LearnedMapEntry map : maps) {
            if (!isTxnType(map.txnType()) || !txnTypeFits(map.txnType(), direction)) {
                continue;
            }
            double score;
            if (map.pattern().equals(pattern)) {
                score = 1;
            } else {
                Set<String> theirs = tokensOf(map.pattern());
                int inter = 0;
                for (String t : mine) {
                    if (theirs.contains(t)) {
                        inter += 1;
                    }
                }
                int union = mine.size() + theirs.size() - inter;
                score = union == 0 ? 0 : (double) inter / union;
                if (score < MIN_JACCARD) {
                    continue;
                }
            }
            if (best == null || score > bestScore || (score == bestScore && map.hits() > best.hits())) {
                best = map;
                bestScore = score;
            }
        }
        return best;
    }

    // ───────────────────────────── klasifikasi ─────────────────────────────

    /** Usulan engine/manusia untuk satu baris. {@code txnType}/{@code confidence}/{@code reason} = node JSON mentah. */
    record EngineInput(JsonNode txnType, String accountId, String accountProblem, JsonNode confidence, JsonNode reason) {
    }

    record ClassifyInput(String direction, EngineInput engine, LearnedMapEntry learned, String learnedAccountLabel,
                         String engineAccountLabel, String cashAccountId, String advanceAccountId,
                         String defaultExpenseAccountId, String defaultIncomeAccountId) {
    }

    record ClassifyResult(String txnType, String accountId, double confidence, boolean needsReview, String reason,
                          String source, boolean contradiction) {
    }

    /** Klasifikasi deterministik satu baris (lihat header kelas untuk aturan jurnal). */
    static ClassifyResult classifyRow(ClassifyInput input) {
        String direction = input.direction();
        EngineInput engine = input.engine();
        List<String> reasons = new ArrayList<>();
        if (engine.reason() != null && engine.reason().isTextual() && !JsCompat.trim(engine.reason().textValue()).isEmpty()) {
            reasons.add(JsCompat.slice(JsCompat.trim(engine.reason().textValue()), 200));
        }
        double confidence = clampConfidence(engine.confidence());

        // Arah kas yang menentukan txnType, bukan jenis dari engine.
        String engineType = null;
        JsonNode rawType = engine.txnType();
        if (rawType != null && rawType.isTextual() && isTxnType(rawType.textValue())) {
            engineType = rawType.textValue();
        } else if (rawType != null && !rawType.isMissingNode() && !rawType.isNull()
                && !(rawType.isTextual() && rawType.textValue().isEmpty())) {
            reasons.add("jenis transaksi \"" + JsCompat.string(rawType) + "\" tidak dikenal; diperkirakan dari arah kas");
        }
        String txnType = engineType != null ? engineType : defaultTxnType(direction);
        boolean contradiction = false;
        String engineAccountId = engine.accountId();
        if (engineType != null && !txnTypeFits(engineType, direction)) {
            contradiction = true;
            txnType = coerceTxnType(engineType, direction);
            reasons.add("jenis \"" + engineType + "\" tidak cocok dengan arah kas " + direction + "; dikoreksi menjadi \"" + txnType + "\"");
            confidence = Math.min(confidence, 0.5);
            // Akun dipilih untuk jenis transaksi yang salah (mis. akun pendapatan untuk kas keluar):
            // jangan dibawa, kembali ke peta terpelajari / akun default arah sebenarnya.
            engineAccountId = null;
        }
        if (engineAccountId != null && engineAccountId.equals(input.cashAccountId())) {
            reasons.add("akun lawan sama dengan akun kas; diabaikan");
            engineAccountId = null;
        } else if (engineAccountId == null && engine.accountProblem() != null && !engine.accountProblem().isEmpty()) {
            reasons.add(engine.accountProblem());
        }

        String accountId = null;
        String source = "none";
        if (isAdvanceType(txnType)) {
            accountId = input.advanceAccountId();
            source = accountId != null ? "advance" : "none";
            if (accountId == null) {
                reasons.add("akun sistem Uang Muka Karyawan belum dipetakan (Pengaturan > Akun Sistem)");
            }
        } else {
            LearnedMapEntry learned = input.learned() != null && !input.learned().accountId().equals(input.cashAccountId())
                    ? input.learned() : null;
            if (engineAccountId != null && confidence >= REVIEW_THRESHOLD) {
                accountId = engineAccountId;
                source = "engine";
                if (learned != null && !learned.accountId().equals(engineAccountId)) {
                    reasons.add("pola terpelajari menyarankan " + (input.learnedAccountLabel() != null ? input.learnedAccountLabel() : "akun lain")
                            + " (tidak diterapkan: keyakinan engine tinggi)");
                }
            } else if (learned != null) {
                accountId = learned.accountId();
                source = "learned";
                confidence = Math.max(confidence, learnedConfidence(learned.hits()));
                reasons.add("akun dari pola terpelajari (" + learned.hits() + "x dikoreksi manusia)");
                if (engineAccountId != null && !engineAccountId.equals(learned.accountId())) {
                    reasons.add("menggantikan usulan engine berkeyakinan rendah ("
                            + (input.engineAccountLabel() != null ? input.engineAccountLabel() : "akun lain") + ")");
                }
            } else if (engineAccountId != null) {
                accountId = engineAccountId;
                source = "engine";
            } else {
                String fallback = "keluar".equals(direction) ? input.defaultExpenseAccountId() : input.defaultIncomeAccountId();
                confidence = Math.min(confidence, 0.3);
                if (fallback != null && !fallback.equals(input.cashAccountId())) {
                    accountId = fallback;
                    source = "default";
                    reasons.add("keluar".equals(direction)
                            ? "belum terklasifikasi; akun usulan default beban lain-lain"
                            : "belum terklasifikasi; akun usulan default pendapatan lain-lain");
                } else {
                    reasons.add("akun lawan belum dipilih");
                }
            }
        }

        boolean needsReview = confidence < REVIEW_THRESHOLD || "lainnya".equals(txnType) || contradiction
                || accountId == null || "default".equals(source);
        if ("lainnya".equals(txnType)) {
            reasons.add("jenis \"lainnya\": wajib ditinjau");
        }
        return new ClassifyResult(txnType, accountId, confidence, needsReview, mergeReasons(reasons), source, contradiction);
    }

    // ───────────────────────────── edit (hanya Draft) ─────────────────────────────

    record EditState(String direction, String txnType, String accountId) {
    }

    /**
     * Patch edit manusia. {@code accountIdTouched=false} = tidak diubah; {@code true} dengan
     * {@code accountId=null} = dikosongkan.
     */
    record EditPatch(String direction, String txnType, boolean accountIdTouched, String accountId) {
    }

    /** Terapkan edit manusia dengan aturan yang sama (jenis uang muka memaksa akun uang muka). */
    static RowResult<EditState> applyRowEdit(EditState current, EditPatch patch, String advanceAccountId) {
        String direction = patch.direction() != null ? patch.direction() : current.direction();
        String txnType = patch.txnType() != null ? patch.txnType() : current.txnType();
        if (!txnTypeFits(txnType, direction)) {
            if (patch.txnType() != null && patch.direction() == null) {
                return RowResult.fail("Jenis \"" + txnType + "\" tidak cocok dengan arah kas " + direction + ".");
            }
            txnType = coerceTxnType(txnType, direction);
        }
        String accountId = patch.accountIdTouched() ? patch.accountId() : current.accountId();
        if (isAdvanceType(txnType)) {
            if (advanceAccountId == null) {
                return RowResult.fail("Akun sistem \"Uang Muka Karyawan\" belum dipetakan (Pengaturan > Akun Sistem).");
            }
            accountId = advanceAccountId;
        } else if (isAdvanceType(current.txnType()) && !patch.accountIdTouched()
                && java.util.Objects.equals(accountId, advanceAccountId)) {
            accountId = null; // keluar dari jenis uang muka: pengguna wajib memilih akun lawan
        }
        return RowResult.ok(new EditState(direction, txnType, accountId));
    }

    // ───────────────────────────── jurnal ─────────────────────────────

    record JournalLineDraft(String accountId, BigDecimal debit, BigDecimal credit, String description) {
    }

    /** Satu jurnal seimbang per baris. Arah tidak pernah diambil dari txnType. */
    static RowResult<List<JournalLineDraft>> buildRowJournalLines(String direction, String txnType, BigDecimal rawAmount,
                                                                  String accountId, String description,
                                                                  String cashAccountId, String narration) {
        if (!isDirection(direction)) {
            return RowResult.fail("Arah baris tidak valid.");
        }
        if (!isTxnType(txnType) || !txnTypeFits(txnType, direction)) {
            return RowResult.fail("Jenis transaksi tidak cocok dengan arah kas " + direction + ".");
        }
        BigDecimal amount = roundMoney(rawAmount);
        if (amount.signum() <= 0) {
            return RowResult.fail("Nominal harus lebih dari 0.");
        }
        if (accountId == null || accountId.isEmpty()) {
            return RowResult.fail("Akun lawan belum dipilih.");
        }
        if (accountId.equals(cashAccountId)) {
            return RowResult.fail("Akun lawan tidak boleh sama dengan akun kas.");
        }
        String desc = JsCompat.slice(description, 250);
        String narr = JsCompat.slice(narration, 250);
        BigDecimal zero = BigDecimal.ZERO;
        List<JournalLineDraft> lines = "keluar".equals(direction)
                ? List.of(new JournalLineDraft(accountId, amount, zero, desc),
                        new JournalLineDraft(cashAccountId, zero, amount, narr))
                : List.of(new JournalLineDraft(cashAccountId, amount, zero, narr),
                        new JournalLineDraft(accountId, zero, amount, desc));
        return RowResult.ok(lines);
    }

    // ───────────────────────────── total / cek saldo ─────────────────────────────

    record TotalsRow(BigDecimal amount, String direction, boolean skip) {
    }

    record Totals(BigDecimal masuk, BigDecimal keluar, int rowCount, int activeCount) {
        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("masuk", JsCompat.jsonNumber(masuk));
            m.put("keluar", JsCompat.jsonNumber(keluar));
            m.put("rowCount", rowCount);
            m.put("activeCount", activeCount);
            return m;
        }
    }

    static Totals computeTotals(List<TotalsRow> rows) {
        BigDecimal masuk = BigDecimal.ZERO;
        BigDecimal keluar = BigDecimal.ZERO;
        int active = 0;
        for (TotalsRow r : rows) {
            if (r.skip()) {
                continue;
            }
            active += 1;
            if ("masuk".equals(r.direction())) {
                masuk = masuk.add(r.amount());
            } else if ("keluar".equals(r.direction())) {
                keluar = keluar.add(r.amount());
            }
        }
        return new Totals(roundMoney(masuk), roundMoney(keluar), rows.size(), active);
    }

    private static String fmtIdr(BigDecimal n) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        nf.setRoundingMode(RoundingMode.HALF_EVEN);
        return nf.format(n);
    }

    /** saldo awal + Σmasuk − Σkeluar vs saldo akhir (baris tidak dilewati). Peringatan, bukan blokir. */
    static Map<String, Object> balanceCheck(BigDecimal opening, BigDecimal closing, List<TotalsRow> rows) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (opening == null || closing == null) {
            m.put("checked", false);
            m.put("expectedClosing", null);
            m.put("difference", null);
            m.put("ok", true);
            m.put("warning", null);
            return m;
        }
        Totals t = computeTotals(rows);
        BigDecimal expected = roundMoney(opening.add(t.masuk()).subtract(t.keluar()));
        BigDecimal diff = roundMoney(closing.subtract(expected));
        boolean ok = diff.signum() == 0;
        m.put("checked", true);
        m.put("expectedClosing", JsCompat.jsonNumber(expected));
        m.put("difference", JsCompat.jsonNumber(diff));
        m.put("ok", ok);
        m.put("warning", ok ? null
                : "Saldo akhir tidak cocok: saldo awal + masuk − keluar = " + fmtIdr(expected) + ", saldo akhir tertulis "
                + fmtIdr(roundMoney(closing)) + " (selisih " + fmtIdr(diff) + "). Periksa baris yang terlewat atau salah baca.");
        return m;
    }

    /** Panduan jenis transaksi untuk engine/UI — kosakata yang sama dengan yang ditegakkan server. */
    static List<Map<String, Object>> txnTypeGuide() {
        return Arrays.asList(
                guide("pengeluaran", "keluar", "DR akun lawan (beban) / CR kas", "pilih akun beban/aset yang tepat dari katalog"),
                guide("pemasukan", "masuk", "DR kas / CR akun lawan (sumber)", "pilih akun pendapatan/sumber dana dari katalog"),
                guide("uang_muka", "keluar", "DR Uang Muka Karyawan / CR kas",
                        "akun ditetapkan server (role EMPLOYEE_ADVANCE); kasbon/uang muka untuk karyawan"),
                guide("terima_kasbon", "masuk", "DR kas / CR Uang Muka Karyawan",
                        "akun ditetapkan server (role EMPLOYEE_ADVANCE); karyawan mengembalikan kasbon"),
                guide("lainnya", "keluar atau masuk", "keluar: DR akun usulan / CR kas; masuk: DR kas / CR akun usulan",
                        "tidak yakin; selalu ditinjau manusia"));
    }

    private static Map<String, Object> guide(String type, String direction, String journal, String rule) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("txnType", type);
        m.put("direction", direction);
        m.put("journal", journal);
        m.put("accountRule", rule);
        return m;
    }
}
