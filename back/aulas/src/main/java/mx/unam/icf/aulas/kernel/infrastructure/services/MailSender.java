package mx.unam.icf.aulas.kernel.infrastructure.services;

import java.util.List;

/**
 * Contract for sending emails from infrastructure services.
 * @author Ithera
 * @version 1.0
 */
public interface MailSender {

    /**
     * Sends an HTML email.
     *
     * @param to       recipient address
     * @param subject  email subject
     * @param htmlBody HTML body content
     */
    void sendHtml(String to, String subject, String htmlBody);

    /**
     * Sends an HTML email with BCC recipients.
     *
     * @param to            primary recipient address
     * @param subject       email subject
     * @param htmlBody      HTML body content
     * @param bccRecipients addresses to receive a CC copy
     */
    void sendHtml(String to, String subject, String htmlBody, List<String> bccRecipients);
}

