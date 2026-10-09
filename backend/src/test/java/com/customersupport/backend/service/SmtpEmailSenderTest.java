package com.customersupport.backend.service;

import com.customersupport.backend.config.EmailProperties;
import com.customersupport.backend.dto.OutboundEmail;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendsFromSupportAddress_withOurThreadingHeaders() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        SmtpEmailSender sender = new SmtpEmailSender(mailSender,
                new EmailProperties("support@test.example", "Acme Support", null, false));

        sender.send(new OutboundEmail("jane@example.com", "Re: Invoice [#3]", "Refunded.",
                "<ours@test.example>", "<theirs@mail.example.com>", "<theirs@mail.example.com>"));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        message.saveChanges(); // what the real transport does before sending; must keep our Message-ID

        assertThat(message.getMessageID()).isEqualTo("<ours@test.example>");
        assertThat(message.getHeader("In-Reply-To", null)).isEqualTo("<theirs@mail.example.com>");
        assertThat(message.getHeader("References", null)).isEqualTo("<theirs@mail.example.com>");
        assertThat(((InternetAddress) message.getFrom()[0]).getAddress()).isEqualTo("support@test.example");
        assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("Acme Support");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("jane@example.com");
        assertThat(message.getSubject()).isEqualTo("Re: Invoice [#3]");
        assertThat(message.getContent()).isEqualTo("Refunded.");
    }
}
