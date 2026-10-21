package id.enterpriseapps.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private AppSettingsService appSettingsService;

    @Value("${spring.mail.from:noreply@irms.id}")
    private String fromAddress;

    @Value("${spring.mail.from-name:IRMS Notification}")
    private String fromName;

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * Send an HTML email using a static HTML template with {{placeholder}} replacement.
     *
     * @param to           recipient email address
     * @param subject      email subject line
     * @param templateName template path relative to classpath:templates/ (e.g. "email/workflow-01-new-report")
     * @param variables    map of placeholder name → value (e.g. "recipientName" replaces {{recipientName}})
     */
    public void sendTemplateEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            String template = loadTemplate(templateName);
            String htmlBody = resolvePlaceholders(template, withBranding(variables));

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Email sent successfully to {} with subject: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {} with subject: {} - {}", to, subject, e.getMessage(), e);
        }
    }

    /**
     * Send an HTML email to multiple recipients.
     */
    public void sendTemplateEmail(String[] to, String subject, String templateName, Map<String, Object> variables) {
        for (String recipient : to) {
            sendTemplateEmail(recipient, subject, templateName, variables);
        }
    }

    private String loadTemplate(String templateName) throws IOException {
        return templateCache.computeIfAbsent(templateName, name -> {
            String path = "templates/" + name + ".html";
            try (InputStream is = new ClassPathResource(path).getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Email template not found: " + path, e);
            }
        });
    }

    /**
     * Menambahkan {{siteName}} & {{siteDesc}} dari Pengaturan Umum ke setiap
     * template, supaya branding email tidak perlu diulang di tiap pemanggil.
     * Nilai dari pemanggil menang bila namanya bentrok.
     */
    private Map<String, Object> withBranding(Map<String, Object> variables) {
        Map<String, Object> merged = new HashMap<>();
        merged.put("siteName", appSettingsService.getSiteName());
        merged.put("siteDesc", appSettingsService.getSiteDesc());
        merged.putAll(variables);
        return merged;
    }

    private String resolvePlaceholders(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace("{{" + entry.getKey() + "}}", value);
        }
        return result;
    }
}

