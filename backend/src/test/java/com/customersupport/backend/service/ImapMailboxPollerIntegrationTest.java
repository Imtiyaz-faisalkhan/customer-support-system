package com.customersupport.backend.service;

import com.customersupport.backend.dto.OutboundEmail;
import com.customersupport.backend.dto.TicketDetail;
import com.customersupport.backend.repository.TicketRepository;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** Polls a real (in-memory GreenMail) IMAP server, the way production polls the IONOS mailbox. */
@SpringBootTest(properties = {
        "app.email.inbound=imap",
        "app.email.imap.host=127.0.0.1",
        "app.email.imap.port=3143",          // ServerSetupTest.IMAP
        "app.email.imap.ssl=false",
        "app.email.imap.username=support@test.example",
        "app.email.imap.password=mailbox-secret",
        "app.email.imap.initial-delay=1h",   // the test polls explicitly; keep the schedule out of the way
})
@ActiveProfiles("test")
class ImapMailboxPollerIntegrationTest {

    @RegisterExtension
    static final GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP)
            .withConfiguration(GreenMailConfiguration.aConfig()
                    .withUser("support@test.example", "support@test.example", "mailbox-secret"));

    @Autowired
    private ImapMailboxPoller poller;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
    }

    private void deliverToSupport(String from, String subject, String body, String messageId, String inReplyTo)
            throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties())) {
            @Override
            protected void updateMessageID() throws jakarta.mail.MessagingException {
                setHeader("Message-ID", messageId);
            }
        };
        message.setFrom(new InternetAddress(from));
        message.setRecipients(MimeMessage.RecipientType.TO, "support@test.example");
        message.setSubject(subject);
        message.setText(body);
        if (inReplyTo != null) {
            message.setHeader("In-Reply-To", inReplyTo);
        }
        message.saveChanges();
        GreenMailUser support = greenMail.getUserManager().getUser("support@test.example");
        support.deliver(message);
    }

    private int folderSize(String folder) throws Exception {
        GreenMailUser support = greenMail.getUserManager().getUser("support@test.example");
        return greenMail.getManagers().getImapHostManager().getFolder(support, folder).getMessageCount();
    }

    @Test
    void importsNewEmails_threadsReplies_andMovesThemOutOfTheInbox() throws Exception {
        deliverToSupport("Sam Carter <sam@example.com>", "Package arrived damaged", "The screen is cracked.",
                "<c1@example.com>", null);

        assertThat(poller.poll()).isEqualTo(1);
        assertThat(folderSize("INBOX")).isZero();
        assertThat(folderSize("Imported")).isEqualTo(1);
        TicketDetail ticket = ticketService.get(ticketRepository.findAll().getFirst().getId());
        assertThat(ticket.customerEmail()).isEqualTo("sam@example.com");
        assertThat(ticket.customerName()).isEqualTo("Sam Carter");
        assertThat(ticket.subject()).isEqualTo("Package arrived damaged");

        // Admin replies; the customer answers that email, which lands in the mailbox.
        ticketService.reply(ticket.id(), "Replacement on its way.");
        ArgumentCaptor<OutboundEmail> sent = ArgumentCaptor.forClass(OutboundEmail.class);
        verify(emailSender).send(sent.capture());
        deliverToSupport("sam@example.com", "Re: Package arrived damaged [#" + ticket.id() + "]",
                "Great, thanks!\n\nOn Thu, Support wrote:\n> Replacement on its way.", "<c2@example.com>",
                sent.getValue().messageId());
        // ...plus a bounce, which must be moved out of the inbox but not become a ticket.
        deliverToSupport("MAILER-DAEMON@mx.example.com", "Undelivered Mail", "Delivery failed", "<b1@x>", null);

        assertThat(poller.poll()).isEqualTo(2);
        assertThat(folderSize("INBOX")).isZero();
        assertThat(folderSize("Imported")).isEqualTo(3);
        assertThat(ticketRepository.count()).isEqualTo(1);
        assertThat(ticketService.get(ticket.id()).messages())
                .extracting(m -> m.author() + ": " + m.body())
                .containsExactly("CUSTOMER: The screen is cracked.", "ADMIN: Replacement on its way.",
                        "CUSTOMER: Great, thanks!");
    }

    @Test
    void emptyInbox_isANoOp() throws Exception {
        assertThat(poller.poll()).isZero();
    }
}
