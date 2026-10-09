package com.customersupport.backend.service;

import com.customersupport.backend.dto.InboundEmail;
import com.customersupport.backend.dto.ResendReceivedEmail;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboundEmailParserTest {

    private final InboundEmailParser parser = new InboundEmailParser();

    private static Map<String, String> fields(String from, String subject, String text, String headers) {
        Map<String, String> fields = new HashMap<>();
        fields.put("from", from);
        fields.put("subject", subject);
        fields.put("text", text);
        fields.put("headers", headers);
        return fields;
    }

    @Test
    void parsesSenderSubjectBodyAndThreadingHeaders() {
        String headers = """
                From: Jane Doe <Jane@Example.com>
                Message-ID: <new-1@mail.example.com>
                In-Reply-To: <ours-2@support.test>
                References: <first-0@mail.example.com>
                 <ours-2@support.test>
                Subject: Re: Invoice
                """;

        InboundEmail email = parser.parse(fields("Jane Doe <Jane@Example.com>", "Re: Invoice", "Thanks!", headers));

        assertThat(email.fromAddress()).isEqualTo("jane@example.com");
        assertThat(email.fromName()).isEqualTo("Jane Doe");
        assertThat(email.subject()).isEqualTo("Re: Invoice");
        assertThat(email.body()).isEqualTo("Thanks!");
        assertThat(email.messageId()).isEqualTo("<new-1@mail.example.com>");
        // In-Reply-To first, then References newest-first, de-duplicated.
        assertThat(email.threadIds()).containsExactly("<ours-2@support.test>", "<first-0@mail.example.com>");
        assertThat(email.automated()).isFalse();
    }

    @Test
    void senderWithoutDisplayName_andMissingSubject() {
        InboundEmail email = parser.parse(fields("bob@example.com", "  ", "Hi", ""));

        assertThat(email.fromName()).isNull();
        assertThat(email.subject()).isEqualTo("(no subject)");
        assertThat(email.messageId()).isNull();
        assertThat(email.threadIds()).isEmpty();
    }

    @Test
    void invalidSender_isRejected() {
        assertThatThrownBy(() -> parser.parse(fields("not an address", "Hi", "Hi", "")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void htmlOnlyEmail_isConvertedToPlainText() {
        Map<String, String> fields = fields("bob@example.com", "Hi", null, "");
        fields.put("html", "<div>Hello &amp; welcome<br>Line two</div><p>Para <b>bold</b></p><script>x()</script>");

        String body = parser.parse(fields).body();

        assertThat(body).isEqualTo("Hello & welcome\nLine two\nPara bold");
    }

    @Test
    void quotedHistory_isStripped() {
        String gmail = """
                Still broken, see attached.

                On Tue, 7 Oct 2026 at 10:00, Customer Support <support@example.com>
                wrote:
                > Hi Jane, please try again.
                > Thanks
                """;
        String outlook = """
                Thanks, that worked.

                -----Original Message-----
                From: Customer Support
                Sent: Tuesday
                """;
        String trailingQuote = "Yes please.\n\n> Do you want a refund?\n> \n";

        assertThat(InboundEmailParser.stripQuotedHistory(gmail)).isEqualTo("Still broken, see attached.");
        assertThat(InboundEmailParser.stripQuotedHistory(outlook)).isEqualTo("Thanks, that worked.");
        assertThat(InboundEmailParser.stripQuotedHistory(trailingQuote)).isEqualTo("Yes please.");
    }

    @Test
    void stripping_neverLeavesAnEmptyBody() {
        String onlyQuote = "> just a forwarded quote\n> nothing else";

        assertThat(InboundEmailParser.stripQuotedHistory(onlyQuote)).isEqualTo(onlyQuote);
    }

    @Test
    void attachmentsAreNotedAndAutoRepliesFlagged() {
        Map<String, String> fields = fields("bot@example.com", "Out of office", "I'm away",
                "Auto-Submitted: auto-replied\n");
        fields.put("attachments", "2");

        InboundEmail email = parser.parse(fields);

        assertThat(email.body()).endsWith("[2 attachments not imported]");
        assertThat(email.automated()).isTrue();
        assertThat(parser.parse(fields("a@example.com", "x", "y", "Precedence: bulk\n")).automated()).isTrue();
        assertThat(parser.parse(fields("a@example.com", "x", "y", "Auto-Submitted: no\n")).automated()).isFalse();
    }

    @Test
    void mimeMessage_multipartWithHtmlAndAttachment() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("jane@example.com", "Jane Doe", "UTF-8"));
        message.setSubject("Invoice question");
        message.setHeader("Message-ID", "<m1@mail.example.com>");
        message.setHeader("In-Reply-To", "<ours-1@test.example>");
        MimeMultipart alternative = new MimeMultipart("alternative");
        MimeBodyPart text = new MimeBodyPart();
        text.setText("Where is my invoice?", "UTF-8");
        MimeBodyPart html = new MimeBodyPart();
        html.setContent("<p>Where is my <b>invoice</b>?</p>", "text/html; charset=UTF-8");
        alternative.addBodyPart(text);
        alternative.addBodyPart(html);
        MimeBodyPart alternativeWrapper = new MimeBodyPart();
        alternativeWrapper.setContent(alternative);
        MimeBodyPart attachment = new MimeBodyPart();
        attachment.setContent("PDFDATA", "application/pdf");
        attachment.setFileName("receipt.pdf");
        MimeMultipart mixed = new MimeMultipart("mixed");
        mixed.addBodyPart(alternativeWrapper);
        mixed.addBodyPart(attachment);
        message.setContent(mixed);
        message.saveChanges();

        InboundEmail email = parser.parse(message);

        assertThat(email.fromAddress()).isEqualTo("jane@example.com");
        assertThat(email.fromName()).isEqualTo("Jane Doe");
        assertThat(email.subject()).isEqualTo("Invoice question");
        assertThat(email.body()).isEqualTo("Where is my invoice?\n\n[1 attachment not imported]");
        assertThat(email.threadIds()).containsExactly("<ours-1@test.example>");
        assertThat(email.automated()).isFalse();
    }

    @Test
    void bouncesFromMailerDaemon_areFlaggedAutomated() {
        assertThat(parser.parse(fields("MAILER-DAEMON@mx.example.com", "Undelivered", "failed", "")).automated())
                .isTrue();
        assertThat(parser.parse(fields("a@example.com", "x", "y",
                "Content-Type: multipart/report; report-type=delivery-status\n")).automated()).isTrue();
    }

    @Test
    void resendEmail_usesDisplayNameAndThreadingFromLowercaseHeaders() {
        ResendReceivedEmail received = new ResendReceivedEmail("e1", "jane@example.com", "Re: Invoice [#3]",
                null, "<p>Still waiting</p>",
                Map.of("from", "Jane Doe <jane@example.com>", "in-reply-to", "<ours@support.example.com>",
                        "references", "<first@mail.example.com> <ours@support.example.com>"),
                "<m2@mail.example.com>", List.of(Map.of("filename", "a.png")));

        InboundEmail email = parser.parse(received);

        assertThat(email.fromAddress()).isEqualTo("jane@example.com");
        assertThat(email.fromName()).isEqualTo("Jane Doe");
        assertThat(email.body()).isEqualTo("Still waiting\n\n[1 attachment not imported]");
        assertThat(email.messageId()).isEqualTo("<m2@mail.example.com>"); // top-level field, no header needed
        assertThat(email.threadIds()).containsExactly("<ours@support.example.com>", "<first@mail.example.com>");
    }

    @Test
    void overlongSubject_isTruncatedToTheColumnSize() {
        String subject = "x".repeat(400);

        assertThat(parser.parse(fields("a@example.com", subject, "y", "")).subject())
                .hasSize(InboundEmailParser.MAX_SUBJECT_LENGTH);
    }
}
