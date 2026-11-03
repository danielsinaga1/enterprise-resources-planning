package id.neravoxa.erp.core.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Setelan salinan email notifikasi ERP ({@code app.erp.email.*}). Kata sandi SMTP hanya dibaca server dan
 * tidak pernah dikirim ke browser maupun ditulis ke log.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.erp.email")
public class ErpEmailProperties {

    private boolean enabled = false;
    /** Alamat pengirim, mis. {@code "Neravoxa ERP <erp@perusahaan.co.id>"}. */
    private String from = "";
    /** Notifikasi dalam jeda ini digabung menjadi satu email per pengguna. */
    private int digestSeconds = 60;
    private Smtp smtp = new Smtp();

    @Data
    public static class Smtp {
        private String host = "";
        private int port = 587;
        private String username = "";
        private String password = "";
        private boolean auth = true;
        private boolean starttls = true;
        private boolean ssl = false;
    }

    /** Siap kirim: diaktifkan dan host SMTP + pengirim terisi. */
    public boolean configured() {
        return enabled && notBlank(smtp.getHost()) && notBlank(from);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
