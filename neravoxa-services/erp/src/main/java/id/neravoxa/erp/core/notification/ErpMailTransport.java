package id.neravoxa.erp.core.notification;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Pengiriman email lewat SMTP dari {@link ErpEmailProperties}. Sender dibuat sendiri (bukan bean
 * {@code JavaMailSender} auto-config) supaya health check aplikasi tidak bergantung pada server SMTP.
 * Tes menimpanya dengan bean tiruan {@code @Primary}.
 */
@Component
public class ErpMailTransport {

    private final ErpEmailProperties props;
    private volatile JavaMailSenderImpl sender;

    public ErpMailTransport(ErpEmailProperties props) {
        this.props = props;
    }

    /** Kirim satu email teks + HTML. Melempar bila SMTP menolak (pemanggil yang menangani). */
    public void send(String to, String subject, String text, String html) throws Exception {
        JavaMailSenderImpl s = sender();
        MimeMessage message = s.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(props.getFrom());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text, html);
        s.send(message);
    }

    private JavaMailSenderImpl sender() {
        JavaMailSenderImpl s = sender;
        if (s == null) {
            synchronized (this) {
                if (sender == null) {
                    sender = build(props.getSmtp());
                }
                s = sender;
            }
        }
        return s;
    }

    static JavaMailSenderImpl build(ErpEmailProperties.Smtp smtp) {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(smtp.getHost().trim());
        s.setPort(smtp.getPort());
        s.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (smtp.getUsername() != null && !smtp.getUsername().isBlank()) {
            s.setUsername(smtp.getUsername());
            s.setPassword(smtp.getPassword());
        }
        Properties p = s.getJavaMailProperties();
        p.put("mail.smtp.auth", String.valueOf(smtp.isAuth()));
        p.put("mail.smtp.starttls.enable", String.valueOf(smtp.isStarttls()));
        p.put("mail.smtp.ssl.enable", String.valueOf(smtp.isSsl()));
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "15000");
        p.put("mail.smtp.writetimeout", "15000");
        return s;
    }
}
