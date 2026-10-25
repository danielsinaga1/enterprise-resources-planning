package id.neravoxa.erp.purchase;

import java.math.BigDecimal;

/**
 * Aritmetika & format angka gaya JavaScript untuk bagian sumber yang memakai {@code number}
 * (laporan, three-way match, snapshot approval) — bukan untuk nilai yang ditulis ke DB.
 */
final class JsNumbers {

    private static final double MAX_SAFE = 9.007199254740992E15;

    private JsNumbers() {
    }

    /** Angka untuk JSON seperti {@code JSON.stringify}: bilangan bulat tanpa ".0", NaN/Infinity → null. */
    static Number num(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return null;
        }
        if (v == Math.rint(v) && Math.abs(v) < MAX_SAFE) {
            return (long) v;
        }
        return v;
    }

    static Number num(Double v) {
        return v == null ? null : num(v.doubleValue());
    }

    /** {@code Number(x)} untuk Decimal/null (null → 0). */
    static double n(BigDecimal v) {
        return v == null ? 0d : v.doubleValue();
    }

    /** {@code Math.round((v + EPSILON·sign(v)) · 10^dp) / 10^dp} — pembulatan di threeWayMatch/purchaseReports. */
    static double roundEps(double v, int dp) {
        double f = Math.pow(10, dp);
        return Math.round((v + Math.ulp(1.0) * Math.signum(v)) * f) / f;
    }

    /** {@code Math.round(n · 10^d) / 10^d} — pembulatan di approvalEditRequests (tanpa EPSILON). */
    static double round(double v, int dp) {
        double f = Math.pow(10, dp);
        return Math.round(v * f) / f;
    }

    /** {@code String(Number(x))} — teks angka untuk pesan error. */
    static String str(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        if (v.signum() == 0) {
            return "0";
        }
        return v.stripTrailingZeros().toPlainString();
    }
}
