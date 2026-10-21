package id.enterpriseapps.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import id.enterpriseapps.mail.WorkflowMailPort;
import lombok.RequiredArgsConstructor;

/**
 * Implementasi {@link WorkflowMailPort} milik core: meneruskan ke EmailService
 * (yang memuat template dari classpath dan menyisipkan branding).
 */
@Service
@RequiredArgsConstructor
public class WorkflowMailAdapter implements WorkflowMailPort {

    private final EmailService emailService;

    @Override
    public void sendTemplate(String to, String subject, String templateName, Map<String, Object> variables) {
        emailService.sendTemplateEmail(to, subject, templateName, variables);
    }
}
