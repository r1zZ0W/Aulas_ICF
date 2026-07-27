package mx.unam.icf.aulas.kernel.infrastructure.services.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.*;
import mx.unam.icf.aulas.kernel.infrastructure.services.MailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring-based implementation of {@link MailSender} that delegates to {@link JavaMailSender}.
 *
 * <p>Both plain-text and HTML emails are sent as {@link jakarta.mail.internet.MimeMessage}
 * with UTF-8 encoding. The sender address is read from {@code spring.mail.username}; if the
 * property is blank (e.g., in local dev without SMTP), the {@code From} header is omitted.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MailSenderImpl implements MailSender {


    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @Override
    public void sendHtml(String to, String subject, String htmlBody) {
        sendMessage(to, subject, htmlBody, null);
    }

    /**
     * Sends an HTML email with a list of hidden carbon copy (BCC) recipients for privacy.
     *
     * @param to            Primary recipient
     * @param subject       Email subject
     * @param htmlBody      Pre-rendered HTML content
     * @param bccRecipients List of hidden administrator emails
     */
    @Override
    public void sendHtml(String to, String subject, String htmlBody, List<String> bccRecipients) {
        sendMessage(to, subject, htmlBody, bccRecipients);
    }


    /**
     * Processes the email sending logic, including handling BCC recipients and UTF-8 encoding.
     * @param to the super admin.
     * @param subject the matter of the email.
     * @param content the HTML content of the email.
     * @param bccRecipients the list of admins that receives the email in BCC.
     */
    private void sendMessage(String to, String subject, String content, List<String> bccRecipients) {
        try {

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());

            if (from != null && !from.isBlank())
                helper.setFrom(from);

            helper.setTo(to);

            // Process BCC Recipients
            if (bccRecipients != null && !bccRecipients.isEmpty()) {
                String[] bcc = bccRecipients.stream()
                        .filter(email -> email != null && !email.isBlank())
                        .distinct()
                        .toArray(String[]::new);
                if (bcc.length > 0)
                    helper.setBcc(bcc);

            }

            helper.setSubject(subject);
            helper.setText(content, true);

            // This is needed for retrieving the messages, while testing this is disabled.
            // Instead, we mock it through logs.
            javaMailSender.send(message);

            /*
             * log.info("==================================================");
             * log.info("[SIMULACIÓN DE CORREO] Correo NO enviado a SMTP real.");
             * log.info("De: {}", from);
             * log.info("Para: {}", to);
             * log.info("Asunto: {}", subject);
             * log.info("BCC: {}", bccRecipients);
             * log.info("==================================================");
             */


        } catch (MessagingException e) {
            throw new MailSendingException("Failed to send email message.", e);
        }
    }
}

