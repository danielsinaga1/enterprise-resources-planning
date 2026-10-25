package id.neravoxa.service.auth;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Menerjemahkan kegagalan bind Active Directory menjadi penyebab yang spesifik.
 *
 * <p>AD membungkus <em>semua</em> kegagalan autentikasi dalam LDAP error 49 dan hanya menaruh
 * alasan sebenarnya pada potongan {@code data <kode>} di dalam pesan error, misalnya:
 *
 * <pre>
 * [LDAP: error code 49 - 80090308: LdapErr: DSID-0C0903A9, comment:
 *  AcceptSecurityContext error, data 773, v4563]
 * </pre>
 *
 * <p>Spring Security meratakan semua kasus itu menjadi {@code BadCredentialsException("Bad
 * credentials")}. Akibatnya password kedaluwarsa, akun terkunci, dan password memang salah
 * tampak identik — baik di log maupun di layar pengguna. Itulah yang membuat keluhan
 * "sudah ganti password AD tapi tidak bisa login" tidak pernah bisa dilacak: pesannya selalu
 * "NPK atau password salah" padahal penyebabnya berbeda-beda dan sebagian butuh tindakan
 * pengguna (mengganti password di komputer kantor), bukan tindakan administrator.
 */
public enum AdAuthError {

    /** Password memang salah. */
    BAD_PASSWORD("52e", "NPK atau password salah"),

    /** Akun tidak ada di AD (jarang muncul di sini karena pencarian sudah gagal lebih dulu). */
    NO_SUCH_USER("525", "NPK tidak terdaftar di Active Directory. Hubungi administrator."),

    /** Login di luar jam yang diizinkan untuk akun tersebut. */
    NOT_PERMITTED_NOW("530", "Akun ini tidak diizinkan login pada jam sekarang. Hubungi IT."),

    /** Login dari workstation yang tidak diizinkan. */
    NOT_PERMITTED_HERE("531", "Akun ini tidak diizinkan login dari perangkat ini. Hubungi IT."),

    /**
     * Password sudah lewat masa berlaku. Bind AD tetap ditolak walau password yang diketik benar,
     * sampai pengguna menggantinya lewat Windows.
     */
    PASSWORD_EXPIRED("532",
            "Password Active Directory Anda sudah kedaluwarsa. Ganti lewat komputer kantor "
                    + "(Ctrl+Alt+Del > Change a password), lalu login kembali."),

    /** Akun dinonaktifkan oleh administrator. */
    ACCOUNT_DISABLED("533", "Akun Active Directory Anda dinonaktifkan. Hubungi IT."),

    /** Masa berlaku akun (bukan password) sudah habis. */
    ACCOUNT_EXPIRED("701", "Masa berlaku akun Active Directory Anda sudah habis. Hubungi IT."),

    /**
     * Flag "user must change password at next logon" aktif — biasanya setelah IT me-reset password.
     * Bind selalu ditolak sampai pengguna mengganti password sendiri lewat Windows.
     */
    MUST_CHANGE_PASSWORD("773",
            "Password Anda harus diganti terlebih dahulu. Ganti lewat komputer kantor "
                    + "(Ctrl+Alt+Del > Change a password), lalu login kembali."),

    /** Akun terkunci karena terlalu banyak percobaan gagal. */
    ACCOUNT_LOCKED("775",
            "Akun Active Directory Anda terkunci karena terlalu banyak percobaan gagal. "
                    + "Tunggu beberapa saat atau hubungi IT."),

    /** Kode tidak dikenal / tidak ada potongan {@code data} sama sekali. */
    UNKNOWN(null, "NPK atau password salah");

    /** Menangkap potongan "data <hex>" pada pesan error AD. */
    private static final Pattern AD_SUB_CODE = Pattern.compile("data\\s+([0-9a-fA-F]+)");

    /**
     * Batas penelusuran rantai penyebab. Kode ini berjalan di jalur login, jadi rantai yang
     * saling menunjuk (A menyebabkan B, B menyebabkan A) tidak boleh sampai menggantungkan
     * thread request. Rantai exception nyata tidak pernah sedalam ini.
     */
    private static final int MAX_CAUSE_DEPTH = 25;

    private final String code;
    private final String userMessage;

    AdAuthError(String code, String userMessage) {
        this.code = code;
        this.userMessage = userMessage;
    }

    /** Kode {@code data} milik AD, atau {@code null} untuk {@link #UNKNOWN}. */
    public String getCode() {
        return code;
    }

    /** Pesan berbahasa Indonesia yang aman ditampilkan ke pengguna. */
    public String getUserMessage() {
        return userMessage;
    }

    /**
     * Menelusuri seluruh rantai penyebab exception untuk menemukan kode {@code data} dari AD.
     * Penelusuran harus sampai ke akar karena Spring Security membungkus
     * {@code javax.naming.AuthenticationException} — pesan di lapisan terluar hanya
     * "Bad credentials" dan tidak memuat kode apa pun.
     */
    public static AdAuthError from(Throwable error) {
        Throwable t = error;
        for (int depth = 0; t != null && depth < MAX_CAUSE_DEPTH; t = t.getCause(), depth++) {
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            Matcher matcher = AD_SUB_CODE.matcher(message);
            if (matcher.find()) {
                String found = matcher.group(1).toLowerCase();
                for (AdAuthError candidate : values()) {
                    if (found.equals(candidate.code)) {
                        return candidate;
                    }
                }
            }
        }
        return UNKNOWN;
    }

    /**
     * Pesan exception terdalam — dipakai untuk log agar kode AD asli tetap terekam walaupun
     * belum dikenali oleh enum ini.
     */
    public static String rootMessage(Throwable error) {
        Throwable root = error;
        for (int depth = 0; root.getCause() != null && depth < MAX_CAUSE_DEPTH; depth++) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
