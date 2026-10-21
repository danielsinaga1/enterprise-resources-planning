package id.enterpriseapps.mail;

import java.util.Map;

/**
 * Port pengiriman email untuk modul fitur (mis. LIMS).
 *
 * ArchUnit melarang :lims mengimpor {@code id.enterpriseapps.service..}, tempat
 * EmailService berada, supaya LIMS tetap bisa diangkat jadi microservice sendiri.
 * Interface ini tinggal di shared kernel sebagai titik temu: modul fitur
 * bergantung pada abstraksi, core menyediakan implementasinya.
 */
public interface WorkflowMailPort {

    /**
     * Kirim email HTML dari template dengan placeholder {{nama}}.
     *
     * @param to           alamat tujuan
     * @param subject      subjek email
     * @param templateName path template relatif ke classpath:templates/
     *                     (mis. "email/lims-01-new-report")
     * @param variables    peta placeholder → nilai
     */
    void sendTemplate(String to, String subject, String templateName, Map<String, Object> variables);
}
