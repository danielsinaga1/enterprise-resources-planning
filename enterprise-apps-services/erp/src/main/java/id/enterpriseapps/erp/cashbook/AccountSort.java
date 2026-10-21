package id.enterpriseapps.erp.cashbook;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Urutan nomor akun bertitik — port {@code utils/accountSort.ts} ({@code compareAccountNumber}).
 * Segmen dibandingkan secara numerik ("1.4.2" &lt; "1.4.10"); segmen yang tidak ada bernilai -1
 * (induk sebelum anak); hasil NaN dianggap sama, seperti {@code Array.prototype.sort}.
 */
final class AccountSort {

    private AccountSort() {
    }

    static int compare(String a, String b) {
        String[] pa = a.split("\\.", -1);
        String[] pb = b.split("\\.", -1);
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            double x = i < pa.length ? JsCompat.number(pa[i]) : -1;
            double y = i < pb.length ? JsCompat.number(pb[i]) : -1;
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

    /** Sort stabil (merge sort sendiri: komparator di atas tidak dijamin transitif bila ada segmen non-angka). */
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
}
