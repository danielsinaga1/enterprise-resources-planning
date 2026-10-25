package id.neravoxa.erp.cashbook;

import java.math.BigInteger;

/**
 * Perbandingan {@code a.localeCompare(b, undefined, { numeric: true })} yang cukup untuk nomor akun:
 * deret angka dibandingkan sebagai bilangan ("1.10" &gt; "1.9"), bagian lain tanpa membedakan huruf besar
 * lebih dulu, lalu urutan kode karakter sebagai pemutus.
 */
final class NaturalOrder {

    private NaturalOrder() {
    }

    static int compare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i;
                int sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                int c = new BigInteger(a.substring(si, i)).compareTo(new BigInteger(b.substring(sj, j)));
                if (c != 0) {
                    return c;
                }
                continue;
            }
            int c = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
            if (c != 0) {
                return c;
            }
            i++;
            j++;
        }
        int c = Integer.compare(a.length() - i, b.length() - j);
        return c != 0 ? c : a.compareTo(b);
    }
}
