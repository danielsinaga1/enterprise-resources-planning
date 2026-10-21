package id.enterpriseapps.assistant.tools;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalisasi tanggal &amp; waktu untuk tool KPIntar.
 *
 * <p>Ada dua masalah nyata yang ditangani di sini:
 *
 * <ol>
 *   <li><b>Model tidak tahu hari ini tanggal berapa.</b> Gemini tidak punya jam; tanpa
 *       {@link #promptContext()} ia menebak tahun/tanggal saat pengguna bilang "hari ini",
 *       "kemarin", atau "3 hari lalu" — atau menolak mengisi tanggal sama sekali.</li>
 *   <li><b>Service IRMS/LIMS memakai parser yang kaku.</b> {@code IncidentReportService.parseDate}
 *       dan {@code SafetyObservationService.parseFlexibleDate} hanya menerima {@code yyyy-MM-dd}
 *       atau {@code yyyy-MM-dd HH:mm:ss}; bentuk lain ("21 Agustus 2026", "21/08/2026",
 *       "2026-8-5", ISO dengan huruf T) melempar dan muncul ke pengguna sebagai
 *       "Gagal membuat laporan" tanpa sebab yang jelas.</li>
 * </ol>
 *
 * <p>Semua tool menormalkan argumen tanggal lewat kelas ini SEBELUM menyentuh service, dan
 * melaporkan format yang tak dikenali sebagai pesan tool yang bisa dibaca model — sehingga
 * model bisa memperbaiki sendiri, bukan gagal diam-diam.
 *
 * <p>Acuan "hari ini" adalah jam server, sama seperti {@code LocalDateTime.now()} yang dipakai
 * seluruh service, sehingga tanggal hitungan asisten konsisten dengan tanggal yang tercatat
 * modul saat data disimpan.
 */
public final class AssistantDates {

    private AssistantDates() {}

    public static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Map<DayOfWeek, String> HARI = Map.of(
            DayOfWeek.MONDAY, "Senin",
            DayOfWeek.TUESDAY, "Selasa",
            DayOfWeek.WEDNESDAY, "Rabu",
            DayOfWeek.THURSDAY, "Kamis",
            DayOfWeek.FRIDAY, "Jumat",
            DayOfWeek.SATURDAY, "Sabtu",
            DayOfWeek.SUNDAY, "Minggu");

    private static final String[] BULAN = {
            "Januari", "Februari", "Maret", "April", "Mei", "Juni",
            "Juli", "Agustus", "September", "Oktober", "November", "Desember"};

    /** Nama bulan Indonesia + Inggris, lengkap &amp; singkatan, semua huruf kecil. */
    private static final Map<String, Integer> NAMA_BULAN = Map.ofEntries(
            Map.entry("januari", 1), Map.entry("jan", 1), Map.entry("january", 1),
            Map.entry("februari", 2), Map.entry("pebruari", 2), Map.entry("feb", 2), Map.entry("february", 2),
            Map.entry("maret", 3), Map.entry("mar", 3), Map.entry("march", 3),
            Map.entry("april", 4), Map.entry("apr", 4),
            Map.entry("mei", 5), Map.entry("may", 5),
            Map.entry("juni", 6), Map.entry("jun", 6), Map.entry("june", 6),
            Map.entry("juli", 7), Map.entry("jul", 7), Map.entry("july", 7),
            Map.entry("agustus", 8), Map.entry("agt", 8), Map.entry("ags", 8),
            Map.entry("agu", 8), Map.entry("aug", 8), Map.entry("august", 8),
            Map.entry("september", 9), Map.entry("sep", 9), Map.entry("sept", 9),
            Map.entry("oktober", 10), Map.entry("okt", 10), Map.entry("oct", 10), Map.entry("october", 10),
            Map.entry("november", 11), Map.entry("nopember", 11), Map.entry("nov", 11),
            Map.entry("desember", 12), Map.entry("des", 12), Map.entry("dec", 12), Map.entry("december", 12));

    private static final Pattern ISO_LIKE = Pattern.compile("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})$");
    private static final Pattern DMY = Pattern.compile("^(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})$");
    private static final Pattern D_MONTH_Y = Pattern.compile("^(\\d{1,2})\\s+([a-z]+)\\.?,?\\s+(\\d{4})$");
    private static final Pattern MONTH_D_Y = Pattern.compile("^([a-z]+)\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})$");
    private static final Pattern D_MONTH = Pattern.compile("^(\\d{1,2})\\s+([a-z]+)\\.?$");
    private static final Pattern N_HARI =
            Pattern.compile("^(\\d{1,3})\\s+hari\\s+(?:yang\\s+)?(lalu|lampau|lagi|kedepan|ke depan|mendatang)$");
    private static final Pattern N_MINGGU =
            Pattern.compile("^(\\d{1,3})\\s+(?:minggu|pekan)\\s+(?:yang\\s+)?(lalu|lampau|lagi|kedepan|ke depan|mendatang)$");
    private static final Pattern N_BULAN =
            Pattern.compile("^(\\d{1,3})\\s+bulan\\s+(?:yang\\s+)?(lalu|lampau|lagi|kedepan|ke depan|mendatang)$");

    /** Ekor waktu pada string tanggal: "T10:00", " 10:00:00", "T10:00:00.123Z", " 10:00+07:00". */
    private static final Pattern EKOR_WAKTU =
            Pattern.compile("[t ]\\d{1,2}[:.]\\d{2}(?::\\d{2})?(?:\\.\\d+)?(?:z|[+-]\\d{2}:?\\d{2})?$");

    private static final Pattern JAM = Pattern.compile("^(\\d{1,2})[:.](\\d{2})(?:[:.](\\d{2}))?$");
    private static final Pattern JAM_BULAT = Pattern.compile("^(\\d{1,2})$");

    /**
     * Ubah tanggal bebas dari model menjadi {@code yyyy-MM-dd}.
     *
     * <p>Menerima: ISO ({@code 2026-08-21}, {@code 2026-8-5}, dengan/tanpa ekor waktu),
     * {@code 21/08/2026}, {@code 21-08-2026}, "21 Agustus 2026", "August 21, 2026",
     * "21 Agustus" (tahun berjalan), serta acuan relatif Indonesia: "hari ini", "sekarang",
     * "kemarin", "kemarin lusa", "besok", "lusa", "minggu lalu/depan", "bulan lalu/depan",
     * "3 hari lalu", "2 minggu lagi".
     *
     * @return tanggal kanonik, atau {@code null} bila {@code raw} kosong ATAU tidak dikenali.
     *         Pemanggil membedakan keduanya dengan mengecek {@code raw} sendiri.
     */
    public static String normalizeDate(String raw) {
        LocalDate d = parseDate(raw);
        return d == null ? null : d.format(ISO_DATE);
    }

    /** Seperti {@link #normalizeDate(String)} tetapi mengembalikan objek tanggalnya. */
    public static LocalDate parseDate(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;

        // Buang label yang sering ikut terbawa dari kalimat pengguna.
        s = s.replaceAll("^(tanggal|tgl\\.?|pada|per|date)\\s+", "").trim();
        s = s.replaceAll("\\s+", " ");

        LocalDate relatif = parseRelative(s);
        if (relatif != null) return relatif;

        // "2026-08-21T10:00" / "2026-08-21 10:00:00" → buang bagian jamnya.
        s = EKOR_WAKTU.matcher(s).replaceAll("").trim();

        Matcher m = ISO_LIKE.matcher(s);
        if (m.matches()) return build(num(m, 1), num(m, 2), num(m, 3));

        m = DMY.matcher(s);
        if (m.matches()) return build(num(m, 3), num(m, 2), num(m, 1));

        m = D_MONTH_Y.matcher(s);
        if (m.matches()) {
            Integer bulan = NAMA_BULAN.get(m.group(2));
            if (bulan != null) return build(num(m, 3), bulan, num(m, 1));
        }

        m = MONTH_D_Y.matcher(s);
        if (m.matches()) {
            Integer bulan = NAMA_BULAN.get(m.group(1));
            if (bulan != null) return build(num(m, 3), bulan, num(m, 2));
        }

        // "17 Agustus" tanpa tahun — anggap tahun berjalan (kasus paling umum saat melapor).
        m = D_MONTH.matcher(s);
        if (m.matches()) {
            Integer bulan = NAMA_BULAN.get(m.group(2));
            if (bulan != null) return build(LocalDate.now().getYear(), bulan, num(m, 1));
        }

        return null;
    }

    /**
     * Ubah waktu bebas menjadi {@code HH:mm}. Menerima {@code 14:30}, {@code 14.30},
     * {@code 14:30:00}, {@code 9:05}, "jam 14", "14:30 WITA".
     *
     * @return waktu kanonik, atau {@code null} bila kosong/tidak dikenali.
     */
    public static String normalizeTime(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;

        s = s.replaceAll("^(jam|pukul|pkl\\.?|at)\\s+", "").trim();
        s = s.replaceAll("\\s*(wib|wita|wit|waktu setempat)$", "").trim();

        Matcher m = JAM.matcher(s);
        if (m.matches()) {
            int jam = num(m, 1);
            int menit = num(m, 2);
            if (jam > 23 || menit > 59) return null;
            return String.format("%02d:%02d", jam, menit);
        }

        m = JAM_BULAT.matcher(s);
        if (m.matches()) {
            int jam = num(m, 1);
            if (jam > 23) return null;
            return String.format("%02d:00", jam);
        }

        return null;
    }

    /**
     * Blok konteks tanggal yang disuntikkan ke system prompt setiap giliran. Tanpa ini model
     * tidak punya acuan apa pun untuk "hari ini" dan cenderung memakai tahun dari data latihnya.
     */
    public static String promptContext() {
        LocalDate hariIni = LocalDate.now();
        return "\n\nTANGGAL & WAKTU SEKARANG (acuan WAJIB untuk semua perhitungan tanggal — "
                + "jangan pakai tanggal lain dari ingatanmu):\n"
                + "- Hari ini: " + humanize(hariIni) + " → " + hariIni.format(ISO_DATE) + "\n"
                + "- Kemarin: " + hariIni.minusDays(1).format(ISO_DATE)
                + " | Besok: " + hariIni.plusDays(1).format(ISO_DATE)
                + " | Lusa: " + hariIni.plusDays(2).format(ISO_DATE) + "\n"
                + "- Tahun berjalan: " + hariIni.getYear() + ", bulan berjalan: "
                + BULAN[hariIni.getMonthValue() - 1] + " ("
                + String.format("%02d", hariIni.getMonthValue()) + ")\n"
                + "ATURAN TANGGAL:\n"
                + "(a) Isi setiap argumen tanggal dalam format YYYY-MM-DD dan argumen waktu dalam format "
                + "HH:mm (24 jam). Hitung sendiri dari acuan di atas — mis. \"kemarin sore\" menjadi tanggal "
                + hariIni.minusDays(1).format(ISO_DATE) + " jam 16:00.\n"
                + "(b) Bila pengguna menyebut tanggal tanpa tahun (mis. \"17 Agustus\"), pakai tahun berjalan; "
                + "kecuali itu menghasilkan tanggal di masa depan untuk kejadian yang sudah lewat — tanyakan tahunnya.\n"
                + "(c) JANGAN pernah bilang kamu tidak bisa menentukan tanggal, dan jangan menyuruh pengguna "
                + "mengetik tanggal manual bila ia sudah menyebut acuan relatif seperti \"kemarin\".\n"
                + "(d) Bila pengguna sama sekali tidak menyebut tanggal, kosongkan argumennya — jangan mengarang.";
    }

    /* ----------------------------- helper ----------------------------- */

    private static LocalDate parseRelative(String s) {
        LocalDate now = LocalDate.now();
        switch (s) {
            case "hari ini", "hariini", "sekarang", "saat ini", "today", "now":
                return now;
            case "kemarin", "kmrn", "yesterday":
                return now.minusDays(1);
            case "kemarin lusa", "kemarin dulu", "dua hari lalu":
                return now.minusDays(2);
            case "besok", "esok", "esok hari", "tomorrow":
                return now.plusDays(1);
            case "lusa", "besok lusa":
                return now.plusDays(2);
            case "minggu lalu", "pekan lalu", "seminggu lalu":
                return now.minusWeeks(1);
            case "minggu depan", "pekan depan", "seminggu lagi":
                return now.plusWeeks(1);
            case "bulan lalu", "sebulan lalu":
                return now.minusMonths(1);
            case "bulan depan", "sebulan lagi":
                return now.plusMonths(1);
            default:
                break;
        }

        Matcher m = N_HARI.matcher(s);
        if (m.matches()) return geser(now, num(m, 1), m.group(2), "hari");
        m = N_MINGGU.matcher(s);
        if (m.matches()) return geser(now, num(m, 1), m.group(2), "minggu");
        m = N_BULAN.matcher(s);
        if (m.matches()) return geser(now, num(m, 1), m.group(2), "bulan");

        return null;
    }

    private static LocalDate geser(LocalDate now, int jumlah, String arah, String satuan) {
        boolean mundur = arah.startsWith("lalu") || arah.startsWith("lampau");
        int delta = mundur ? -jumlah : jumlah;
        return switch (satuan) {
            case "minggu" -> now.plusWeeks(delta);
            case "bulan" -> now.plusMonths(delta);
            default -> now.plusDays(delta);
        };
    }

    private static LocalDate build(int tahun, int bulan, int hari) {
        try {
            return LocalDate.of(tahun, bulan, hari);
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    private static int num(Matcher m, int group) {
        return Integer.parseInt(m.group(group));
    }

    private static String humanize(LocalDate d) {
        return HARI.get(d.getDayOfWeek()) + ", " + d.getDayOfMonth() + " "
                + BULAN[d.getMonthValue() - 1] + " " + d.getYear();
    }
}
