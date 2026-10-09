package com.customersupport.backend.service;

import com.customersupport.backend.dto.InboundEmail;
import com.customersupport.backend.dto.ResendReceivedEmail;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an email fetched from Resend's receiving API into an {@link InboundEmail}: sender, subject, plain-text body
 * (quoted history stripped), threading IDs and an auto-reply flag. Pure parsing, no persistence, so it's
 * unit-testable on its own.
 */
@Component
public class InboundEmailParser {

    static final int MAX_SUBJECT_LENGTH = 255;       // tickets.subject column size
    static final int MAX_NAME_LENGTH = 255;          // tickets.customer_name column size
    static final int MAX_BODY_LENGTH = 50_000;

    private static final Pattern MESSAGE_ID = Pattern.compile("<[^<>\\s]+>");
    // "On Tue, 7 Oct 2026 at 10:00, Jane <jane@x.com> wrote:" (Gmail/Apple Mail; may wrap onto two lines)
    private static final Pattern ON_WROTE = Pattern.compile("^On\\s.+", Pattern.CASE_INSENSITIVE);
    private static final Pattern OUTLOOK_SEPARATOR =
            Pattern.compile("^(-{2,}\\s*Original Message\\s*-{2,}|_{10,})\\s*$", Pattern.CASE_INSENSITIVE);

    /** An email fetched from Resend's receiving API. @throws IllegalArgumentException if it has no valid sender */
    public InboundEmail parse(ResendReceivedEmail email) {
        Map<String, Object> headers = email.headers() == null ? Map.of() : email.headers();
        // Resend's header map is lowercase-keyed; look up case-insensitively anyway.
        Function<String, String> header = name -> headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null)
                .map(entry -> String.valueOf(entry.getValue()))
                .findFirst()
                .orElse(null);

        // The "from" header keeps the display name ("Jane <jane@x.com>"); the top-level field may be address-only.
        InternetAddress from = parseFrom(header.apply("From") != null ? header.apply("From") : email.from());
        String body = stripQuotedHistory(bodyText(email.text(), email.html()));
        int attachments = email.attachments() == null ? 0 : email.attachments().size();
        if (attachments > 0) {
            body += "\n\n[" + attachments + (attachments == 1 ? " attachment" : " attachments")
                    + " not imported]";
        }
        if (body.length() > MAX_BODY_LENGTH) {
            body = body.substring(0, MAX_BODY_LENGTH) + "\n\n[message truncated]";
        }

        List<String> threadIds = new ArrayList<>(messageIds(header.apply("In-Reply-To")));
        // References is oldest-first; newest first is more likely to be a message we know about.
        List<String> references = new ArrayList<>(messageIds(header.apply("References")));
        threadIds.addAll(references.reversed());

        String fromAddress = from.getAddress().toLowerCase();
        return new InboundEmail(
                fromAddress,
                truncate(blankToNull(from.getPersonal()), MAX_NAME_LENGTH),
                truncate(subjectOrDefault(email.subject()), MAX_SUBJECT_LENGTH),
                body,
                messageId(header.apply("Message-ID"), email),
                List.copyOf(new LinkedHashSet<>(threadIds)),
                isAutomated(fromAddress, header));
    }

    /**
     * The email's own Message-ID, which InboundEmailService uses to drop Resend's retries of the same delivery.
     * An email without one still needs a stable key, so Resend's id for it stands in.
     */
    private static String messageId(String headerValue, ResendReceivedEmail email) {
        List<String> ids = messageIds(headerValue);
        if (ids.isEmpty()) {
            ids = messageIds(email.messageId());
        }
        if (!ids.isEmpty()) {
            return ids.getFirst();
        }
        return email.id() == null ? null : "<" + email.id() + "@resend.invalid>";
    }

    private static InternetAddress parseFrom(String from) {
        if (from == null || from.isBlank()) {
            throw new IllegalArgumentException("Missing sender");
        }
        try {
            InternetAddress[] addresses = InternetAddress.parse(from, false);
            if (addresses.length == 0 || addresses[0].getAddress() == null || !addresses[0].getAddress().contains("@")) {
                throw new IllegalArgumentException("Invalid sender: " + from);
            }
            return addresses[0];
        } catch (AddressException ex) {
            throw new IllegalArgumentException("Invalid sender: " + from, ex);
        }
    }

    private static List<String> messageIds(String headerValue) {
        List<String> ids = new ArrayList<>();
        if (headerValue != null) {
            Matcher matcher = MESSAGE_ID.matcher(headerValue);
            while (matcher.find()) {
                ids.add(matcher.group());
            }
        }
        return ids;
    }

    /**
     * Auto-replies (out-of-office), bounces and bulk mail must not open tickets or loop with our replies.
     * Bounces of our own replies come back to the support address, so they must be recognised too.
     */
    private static boolean isAutomated(String fromAddress, Function<String, String> header) {
        String localPart = fromAddress.substring(0, fromAddress.indexOf('@'));
        if (localPart.equals("mailer-daemon") || localPart.equals("postmaster")) {
            return true;
        }
        String contentType = header.apply("Content-Type");
        if (contentType != null && contentType.toLowerCase().contains("multipart/report")) {
            return true; // delivery status notification
        }
        String autoSubmitted = header.apply("Auto-Submitted");
        if (autoSubmitted != null && !autoSubmitted.trim().equalsIgnoreCase("no")) {
            return true;
        }
        String precedence = header.apply("Precedence");
        return precedence != null && Set.of("bulk", "junk", "list", "auto_reply")
                .contains(precedence.trim().toLowerCase());
    }

    static String bodyText(String text, String html) {
        if (text != null && !text.isBlank()) {
            return text.replace("\r\n", "\n");
        }
        if (html != null && !html.isBlank()) {
            return htmlToText(html);
        }
        return "";
    }

    /** Keeps line structure (br/p/div become newlines), drops all markup, decodes entities. */
    private static String htmlToText(String html) {
        Document document = Jsoup.parse(html);
        document.outputSettings(new Document.OutputSettings().prettyPrint(false));
        document.select("br").after("\\n");
        document.select("p, div, li, tr, h1, h2, h3, h4, h5, h6").before("\\n");
        String withMarkers = document.body().html().replace("\\n", "\n");
        String noTags = Jsoup.clean(withMarkers, "", Safelist.none(),
                new Document.OutputSettings().prettyPrint(false));
        return Parser.unescapeEntities(noTags, false).replaceAll("\n{3,}", "\n\n").strip();
    }

    /**
     * Customers' mail clients quote the whole previous thread under their reply; keep only the new text.
     * Heuristic: cut at an "On ... wrote:" line or an Outlook "Original Message" separator, otherwise drop a
     * trailing block of "&gt;"-quoted lines. If that would leave nothing, the full text is kept.
     */
    static String stripQuotedHistory(String text) {
        List<String> lines = text.lines().toList();
        int cut = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            boolean onWrote = ON_WROTE.matcher(line).matches() && (line.endsWith("wrote:")
                    || (i + 1 < lines.size() && lines.get(i + 1).strip().endsWith("wrote:")));
            if (onWrote || OUTLOOK_SEPARATOR.matcher(line).matches()) {
                cut = i;
                break;
            }
        }
        if (cut == lines.size()) {
            while (cut > 0 && (lines.get(cut - 1).startsWith(">") || lines.get(cut - 1).isBlank())) {
                cut--;
            }
        }
        String kept = String.join("\n", lines.subList(0, cut)).strip();
        return kept.isEmpty() ? text.strip() : kept;
    }

    private static String subjectOrDefault(String subject) {
        return subject == null || subject.isBlank() ? "(no subject)" : subject.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
