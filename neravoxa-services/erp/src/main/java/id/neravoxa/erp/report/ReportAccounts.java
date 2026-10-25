package id.neravoxa.erp.report;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Urutan nomor akun — port {@code utils/accountSort.ts} ({@code compareAccountNumber}).
 * Segmen dibandingkan sebagai {@code Number(segmen)}; segmen yang tidak ada = -1 (induk sebelum anak).
 */
final class ReportAccounts {

    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");
    private static final Pattern RADIX = Pattern.compile("0([xXoObB])([0-9a-fA-F]+)");

    private ReportAccounts() {
    }

    /** {@code Number(s)} JavaScript untuk satu segmen (string kosong = 0, tak valid = NaN). */
    static double jsNumber(String raw) {
        String s = raw.strip();
        if (s.isEmpty()) {
            return 0;
        }
        if (DECIMAL.matcher(s).matches()) {
            return Double.parseDouble(s);
        }
        var m = RADIX.matcher(s);
        if (m.matches()) {
            int radix = switch (Character.toLowerCase(m.group(1).charAt(0))) {
                case 'x' -> 16;
                case 'o' -> 8;
                default -> 2;
            };
            try {
                return new java.math.BigInteger(m.group(2), radix).doubleValue();
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return switch (s) {
            case "Infinity", "+Infinity" -> Double.POSITIVE_INFINITY;
            case "-Infinity" -> Double.NEGATIVE_INFINITY;
            default -> Double.NaN;
        };
    }

    /**
     * "1.4.2" &lt; "1.4.10". Hasil NaN (segmen non-angka) diperlakukan sama (0) seperti
     * {@code Array.prototype.sort}, dan perbandingan berhenti di segmen itu.
     */
    static int compare(String a, String b) {
        String[] pa = a.split("\\.", -1);
        String[] pb = b.split("\\.", -1);
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            double x = i < pa.length ? jsNumber(pa[i]) : -1;
            double y = i < pb.length ? jsNumber(pb[i]) : -1;
            double diff = x - y;
            if (Double.isNaN(diff)) {
                return 0;
            }
            if (diff != 0) {
                return diff < 0 ? -1 : 1;
            }
        }
        return 0;
    }

    /**
     * Salinan terurut stabil (merge sort sendiri: komparator di atas tidak transitif bila ada
     * segmen non-angka, dan TimSort Java bisa menolak komparator seperti itu).
     */
    static <T> List<T> sorted(List<T> items, Function<T, String> accountNumber) {
        List<T> list = new ArrayList<>(items);
        if (list.size() > 1) {
            mergeSort(list, accountNumber, 0, list.size(), new ArrayList<>(list));
        }
        return list;
    }

    private static <T> void mergeSort(List<T> a, Function<T, String> key, int from, int to, List<T> buf) {
        if (to - from < 2) {
            return;
        }
        int mid = (from + to) >>> 1;
        mergeSort(a, key, from, mid, buf);
        mergeSort(a, key, mid, to, buf);
        int i = from;
        int j = mid;
        int k = from;
        while (i < mid && j < to) {
            // <= menjaga stabilitas: elemen kiri menang bila sama.
            if (compare(key.apply(a.get(i)), key.apply(a.get(j))) <= 0) {
                buf.set(k++, a.get(i++));
            } else {
                buf.set(k++, a.get(j++));
            }
        }
        while (i < mid) {
            buf.set(k++, a.get(i++));
        }
        while (j < to) {
            buf.set(k++, a.get(j++));
        }
        for (int x = from; x < to; x++) {
            a.set(x, buf.get(x));
        }
    }
}
