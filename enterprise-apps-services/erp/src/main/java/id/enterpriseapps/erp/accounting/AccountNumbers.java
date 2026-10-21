package id.enterpriseapps.erp.accounting;

import id.enterpriseapps.erp.entity.RootType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Aturan nomor akun COA — port {@code utils/accountSort.ts} dan {@code deriveRootType} di
 * {@code routes/coa.ts}.
 */
final class AccountNumbers {

    private AccountNumbers() {
    }

    /**
     * Bandingkan nomor akun bertitik secara numerik: "1.4.2" &lt; "1.4.10"; segmen yang tidak ada
     * bernilai -1 (induk sebelum anak). Segmen non-angka (NaN) dianggap sama, seperti
     * {@code Array.prototype.sort} memperlakukan hasil NaN.
     */
    static int compare(String a, String b) {
        String[] pa = a.split("\\.", -1);
        String[] pb = b.split("\\.", -1);
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            double x = i < pa.length ? JsNumbers.number(pa[i]) : -1;
            double y = i < pb.length ? JsNumbers.number(pb[i]) : -1;
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
     * Urutkan stabil dengan {@link #compare} (merge sort sendiri: komparator tidak dijamin konsisten
     * bila ada segmen non-angka, dan TimSort Java bisa menolak komparator seperti itu).
     */
    static <T> List<T> sorted(List<T> items, Function<T, String> number) {
        List<T> list = new ArrayList<>(items);
        mergeSort(list, number, 0, list.size(), new ArrayList<>(list));
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
            if (compare(key.apply(a.get(j)), key.apply(a.get(i))) < 0) {
                buf.set(k++, a.get(j++));
            } else {
                buf.set(k++, a.get(i++));
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

    /**
     * Root type dari prefix nomor akun (segmen pertama): 1 aset, 2 kewajiban, 3 ekuitas,
     * 4 pendapatan, 5–8 beban. Menimpa nilai kiriman pengguna agar tidak salah klasifikasi;
     * prefix lain → {@code fallback} (boleh null = "tidak berubah").
     */
    static RootType deriveRootType(String accountNumber, RootType fallback) {
        int dot = accountNumber.indexOf('.');
        String prefix = dot < 0 ? accountNumber : accountNumber.substring(0, dot);
        return switch (prefix) {
            case "1" -> RootType.ASSET;
            case "2" -> RootType.LIABILITY;
            case "3" -> RootType.EQUITY;
            case "4" -> RootType.REVENUE;
            case "5", "6", "7", "8" -> RootType.EXPENSE;
            default -> fallback;
        };
    }
}
