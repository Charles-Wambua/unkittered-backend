package com.unkittered.api.mail;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailServiceTest {

    @SuppressWarnings("unchecked")
    private MailService service(String host, JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        when(provider.getObject()).thenReturn(sender);
        return new MailService(provider, host, "team@example.com", "", "Unkittered",
                "https://site.example", "help@example.com");
    }

    @Test
    void offWithoutAHost() {
        JavaMailSender sender = mock(JavaMailSender.class);
        MailService mail = service("", sender);
        assertThat(mail.isEnabled()).isFalse();
        assertThat(mail.send("a@b.c", "Hi", "Hi", List.of("x"), null)).isFalse();
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendsABrandedEscapedMessage() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        MailService mail = service("smtp.example.com", sender);

        boolean ok = mail.send("member@example.com", "Merry Christmas 🎄", "Merry <Christmas>",
                List.of("100% love from the pack", "Line one\nline two"),
                new MailService.Action("Open", "https://site.example"));

        assertThat(ok).isTrue();
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sent.capture());
        MimeMessage m = sent.getValue();
        m.saveChanges();
        assertThat(m.getSubject()).isEqualTo("Merry Christmas 🎄");
        assertThat(m.getFrom()[0].toString()).contains("team@example.com");
        assertThat(m.getReplyTo()[0].toString()).isEqualTo("help@example.com");
        String text = part(m.getContent(), "text/html");
        assertThat(text).contains("Merry &lt;Christmas&gt;").contains("100% love from the pack")
                .contains("https://site.example/privacy");
        assertThat(part(m.getContent(), "text/plain")).contains("Line one\nline two");
    }

    /** The decoded body of the first part of the given type. */
    private static String part(Object content, String type) throws Exception {
        if (content instanceof MimeMultipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                var p = mp.getBodyPart(i);
                if (p.isMimeType(type)) return (String) p.getContent();
                String inner = part(p.getContent(), type);
                if (inner != null) return inner;
            }
        }
        return null;
    }
}
