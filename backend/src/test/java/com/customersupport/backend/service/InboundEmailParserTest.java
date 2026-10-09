package com.customersupport.backend.service;

import com.customersupport.backend.dto.InboundEmail;
import com.customersupport.backend.dto.ResendReceivedEmail;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboundEmailParserTest {

    private final InboundEmailParser parser = new InboundEmailParser();

    /** A received email as Resend's GET /emails/receiving/{id} returns it (headers are a lowercase-keyed map). */
    private static ResendReceivedEmail email(String from, String subject, String text, String html,
                                             Map<String, Object> headers, String messageId, int attachments) {
        List<Map<String, Object>> files = java.util.stream.IntStream.range(0, attachments)
                .mapToObj(i -> Map.<String, Object>of("filename", "file" + i + ".pdf"))
                .toList();
        return new ResendReceivedEmail("e1", from, subject, text, html, headers, messageId, files);
    }

    private static InboundEmail parseText(InboundEmailParser parser, String from, String text,
                                          Map<String, Object> headers) {
        return parser.parse(email(from, "Subject", text, null, headers, null, 0));
    }

    @Test
    void usesDisplayNameFromHeaders_andThreadingIds() {
        Map<String, Object> headers = Map.of(
                "from", "Jane Doe <Jane@Example.com>",
                "in-reply-to", "<ours-2@support.test>",
                "references", "<first-0@mail.example.com> <ours-2@support.test>");

        InboundEmail email = parser.parse(email("jane@example.com", "Re: Invoice", "Thanks!", null, headers,
                "<new-1@mail.example.com>", 0));

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
    void fallsBackToTopLevelSender_whenNoFromHeader_andHandlesMissingSubject() {
        InboundEmail email = parser.parse(email("bob@example.com", "  ", "Hi", null, null, null, 0));

        assertThat(email.fromAddress()).isEqualTo("bob@example.com");
        assertThat(email.fromName()).isNull();
        assertThat(email.subject()).isEqualTo("(no subject)");
        // No Message-ID anywhere: Resend's id stands in, so a retried delivery is still recognised as a duplicate.
        assertThat(email.messageId()).isEqualTo("<e1@resend.invalid>");
        assertThat(email.threadIds()).isEmpty();
    }

    @Test
    void invalidSender_isRejected() {
        assertThatThrownBy(() -> parser.parse(email("not an address", "Hi", "Hi", null, null, null, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void htmlOnlyEmail_isConvertedToPlainText() {
        String html = "<div>Hello &amp; welcome<br>Line two</div><p>Para <b>bold</b></p><script>x()</script>";

        String body = parser.parse(email("bob@example.com", "Hi", null, html, null, null, 0)).body();

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
    void attachmentsAreNoted() {
        InboundEmail email = parser.parse(email("a@example.com", "Docs", "See files", null, null, null, 2));

        assertThat(email.body()).isEqualTo("See files\n\n[2 attachments not imported]");
    }

    @Test
    void autoRepliesBouncesAndBulkMail_areFlaggedAutomated() {
        assertThat(parseText(parser, "a@example.com", "Away", Map.of("auto-submitted", "auto-replied")).automated())
                .isTrue();
        assertThat(parseText(parser, "a@example.com", "x", Map.of("precedence", "bulk")).automated()).isTrue();
        assertThat(parseText(parser, "MAILER-DAEMON@mx.example.com", "failed", Map.of()).automated()).isTrue();
        assertThat(parseText(parser, "a@example.com", "x",
                Map.of("content-type", "multipart/report; report-type=delivery-status")).automated()).isTrue();
        assertThat(parseText(parser, "a@example.com", "x", Map.of("auto-submitted", "no")).automated()).isFalse();
    }

    @Test
    void overlongSubject_isTruncatedToTheColumnSize() {
        Map<String, Object> noHeaders = new HashMap<>();

        assertThat(parser.parse(email("a@example.com", "x".repeat(400), "y", null, noHeaders, null, 0)).subject())
                .hasSize(InboundEmailParser.MAX_SUBJECT_LENGTH);
    }
}
