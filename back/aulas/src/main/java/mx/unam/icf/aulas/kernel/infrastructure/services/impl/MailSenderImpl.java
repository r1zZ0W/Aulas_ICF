package mx.unam.icf.aulas.kernel.infrastructure.services.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.*;
import mx.unam.icf.aulas.kernel.infrastructure.services.MailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Spring-based implementation of {@link MailSender} that delegates to {@link JavaMailSender}.
 *
 * <p>Both plain-text and HTML emails are sent as {@link jakarta.mail.internet.MimeMessage}
 * with UTF-8 encoding. The sender address is read from {@code spring.mail.username}; if the
 * property is blank (e.g., in local dev without SMTP), the {@code From} header is omitted.</p>
 */
@Component
@RequiredArgsConstructor
public class MailSenderImpl implements MailSender {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @Override
    public void sendText(String to, String subject, String body) {
        sendMessage(to, subject, body, false);
    }

    @Override
    public void sendHtml(String to, String subject, String htmlBody) {
        sendMessage(to, subject, htmlBody, true);
    }

    private void sendMessage(String to, String subject, String content, boolean html) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());

            if (from != null && !from.isBlank())
                helper.setFrom(from);

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, html);
            javaMailSender.send(message);
        } catch (MessagingException e) {
            throw new MailSendingException("Failed to send email message.", e);
        }
    }
}

