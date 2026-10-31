package com.unkittered.api.mail;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Sends branded transactional email over SMTP (see {@code spring.mail.*}).
 * When no SMTP host is configured, {@link #isEnabled()} is false and sends are
 * logged and skipped, so nothing breaks in development.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String from;
    private final String fromName;
    private final String siteUrl;
    private final String support;

    public MailService(ObjectProvider<JavaMailSender> sender,
                       @Value("${spring.mail.host:}") String host,
                       @Value("${spring.mail.username:}") String username,
                       @Value("${unkittered.mail.from:}") String from,
                       @Value("${unkittered.mail.from-name:Unkittered}") String fromName,
                       @Value("${unkittered.mail.site-url:}") String siteUrl,
                       @Value("${unkittered.mail.support:}") String support) {
        this.sender = sender;
        this.host = host;
        this.from = from.isBlank() ? username : from;
        this.fromName = fromName;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
        this.support = support;
    }

    public boolean isEnabled() {
        return !host.isBlank() && !from.isBlank() && sender.getIfAvailable() != null;
    }

    /** A call to action: a button under the message. */
    public record Action(String label, String url) { }

    /**
     * Sends one email. Paragraphs are plain text (escaped here). Returns false
     * if email is off or sending failed; never throws.
     */
    public boolean send(String to, String subject, String heading, List<String> paragraphs, Action action) {
        if (!isEnabled()) {
            log.info("Email is not configured; skipped \"{}\"", subject);
            return false;
        }
        if (to == null || to.isBlank()) return false;
        try {
            MimeMessage message = sender.getObject().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            if (!support.isBlank()) helper.setReplyTo(support);
            helper.setSubject(subject);
            helper.setText(plain(heading, paragraphs, action), html(heading, paragraphs, action));
            sender.getObject().send(message);
            return true;
        } catch (Exception e) {
            log.warn("Could not send \"{}\": {}", subject, e.getMessage());
            return false;
        }
    }

    /** Same as {@link #send}, off the request thread. */
    @Async
    public void sendAsync(String to, String subject, String heading, List<String> paragraphs, Action action) {
        send(to, subject, heading, paragraphs, action);
    }

    public String siteUrl() {
        return siteUrl;
    }

    private String plain(String heading, List<String> paragraphs, Action action) {
        StringBuilder b = new StringBuilder(heading).append("\n\n");
        paragraphs.forEach(p -> b.append(p).append("\n\n"));
        if (action != null) b.append(action.label()).append(": ").append(action.url()).append("\n\n");
        b.append("— The Unkittered team\n");
        if (!siteUrl.isBlank()) b.append(siteUrl).append("\n");
        return b.toString();
    }

    private String html(String heading, List<String> paragraphs, Action action) {
        StringBuilder body = new StringBuilder();
        for (String p : paragraphs) {
            body.append("<p style=\"margin:0 0 16px;font-size:16px;line-height:1.6;color:#3d322a\">")
                .append(HtmlUtils.htmlEscape(p).replace("\n", "<br>"))
                .append("</p>");
        }
        String button = action == null ? "" : """
                <table role="presentation" cellspacing="0" cellpadding="0" style="margin:8px 0 8px">
                  <tr><td style="border-radius:999px;background:#C96A3A">
                    <a href="%s" style="display:inline-block;padding:14px 26px;font-weight:700;font-size:15px;color:#ffffff;text-decoration:none;border-radius:999px">%s</a>
                  </td></tr>
                </table>""".formatted(HtmlUtils.htmlEscape(action.url()), HtmlUtils.htmlEscape(action.label()));
        String footerLinks = siteUrl.isBlank() ? "" : """
                <a href="%1$s/support" style="color:#A9542A">Help</a> &nbsp;·&nbsp;
                <a href="%1$s/safety" style="color:#A9542A">Safety</a> &nbsp;·&nbsp;
                <a href="%1$s/privacy" style="color:#A9542A">Privacy</a>""".formatted(siteUrl);
        return """
                <!doctype html>
                <html><body style="margin:0;padding:0;background:#F5F0E8">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#F5F0E8;padding:32px 12px">
                  <tr><td align="center">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;font-family:'DM Sans',Helvetica,Arial,sans-serif">
                      <tr><td style="padding:0 8px 20px">
                        <span style="display:inline-block;width:36px;height:36px;border-radius:11px;background:#C96A3A;color:#F5F0E8;text-align:center;line-height:36px;font-size:18px;vertical-align:middle">&#128062;</span>
                        <span style="font-family:Georgia,'Times New Roman',serif;font-size:24px;font-weight:700;color:#1A1410;vertical-align:middle;margin-left:8px">Un<span style="color:#C96A3A">kittered</span></span>
                      </td></tr>
                      <tr><td style="background:#FAF7F2;border:1px solid #E3D7C8;border-radius:24px;padding:32px 28px">
                        <h1 style="margin:0 0 18px;font-family:Georgia,'Times New Roman',serif;font-size:30px;line-height:1.1;color:#1A1410;font-weight:700">%s</h1>
                        %s
                        %s
                        <p style="margin:20px 0 0;font-size:15px;color:#76665A">The Unkittered team</p>
                      </td></tr>
                      <tr><td style="padding:20px 8px;font-size:12.5px;line-height:1.6;color:#76665A">
                        %s<br>
                        You're receiving this because you have an Unkittered account. Reply to this email to reach us.
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
                </body></html>
                """.formatted(HtmlUtils.htmlEscape(heading), body, button, footerLinks);
    }
}
