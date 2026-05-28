package mx.unam.icf.aulas.kernel.infrastructure.services;

/**
 * Contract for sending emails from infrastructure services.
 */
public interface MailSender {

    /**
     * Sends a plain-text email.
     *
     * @param to      recipient address
     * @param subject email subject
     * @param body    plain-text body
     */
    void sendText(String to, String subject, String body);

    /**
     * Sends an HTML email.
     *
     * @param to       recipient address
     * @param subject  email subject
     * @param htmlBody HTML body content
     */
    void sendHtml(String to, String subject, String htmlBody);
}

