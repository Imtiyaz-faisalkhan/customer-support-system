package com.customersupport.backend.service;

import com.customersupport.backend.config.EmailProperties;
import com.customersupport.backend.dto.OutboundEmail;
import com.customersupport.backend.dto.ResendReceivedEmail;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendClientTest {

    @Test
    void fetchesReceivedEmailWithBearerKey_andMapsTheFieldsWeUse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendClient client = new ResendClient(builder, new EmailProperties("support@test.example", "Support",
                new EmailProperties.Resend("re_secret", "whsec_x", "https://api.resend.test"), false));

        // Trimmed copy of the documented response, incl. fields we ignore.
        server.expect(requestTo("https://api.resend.test/emails/receiving/4ef9a417"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer re_secret"))
                .andRespond(withSuccess("""
                        {"object":"email","id":"4ef9a417","to":["help@support.example.com"],
                         "from":"jane@example.com","created_at":"2026-04-03T22:13:42.674Z","subject":"Re: Invoice",
                         "html":"<p>Thanks</p>","html_format":"data_uri","text":"Thanks",
                         "headers":{"from":"Jane Doe <jane@example.com>","in-reply-to":"<ours@support.example.com>"},
                         "authentication":{"spf":"pass","dkim":"pass","dmarc":"pass"},
                         "message_id":"<m1@mail.example.com>",
                         "attachments":[{"id":"a1","filename":"receipt.pdf","content_type":"application/pdf"}]}
                        """, MediaType.APPLICATION_JSON));

        ResendReceivedEmail email = client.getReceivedEmail("4ef9a417");

        server.verify();
        assertThat(email.from()).isEqualTo("jane@example.com");
        assertThat(email.subject()).isEqualTo("Re: Invoice");
        assertThat(email.text()).isEqualTo("Thanks");
        assertThat(email.messageId()).isEqualTo("<m1@mail.example.com>");
        assertThat(email.headers()).containsEntry("in-reply-to", "<ours@support.example.com>");
        assertThat(email.attachments()).hasSize(1);
    }

    @Test
    void sendsEmailWithThreadingAndAutoReplyHeaders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendClient client = new ResendClient(builder, new EmailProperties("support@test.example", "Support",
                new EmailProperties.Resend("re_secret", "whsec_x", "https://api.resend.test"), false));

        server.expect(requestTo("https://api.resend.test/emails"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_secret"))
                .andExpect(content().json("""
                        {"from":"Support <support@test.example>","to":["jane@example.com"],
                         "reply_to":"support@test.example","subject":"Re: Login [#3-ABCDEFGH]",
                         "text":"Try Forgot Password.",
                         "headers":{"Message-ID":"<ours@test.example>","In-Reply-To":"<m1@x>",
                                    "References":"<m1@x>","Auto-Submitted":"auto-replied"}}
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"id\":\"49a3999c\"}", MediaType.APPLICATION_JSON));

        client.sendEmail(new OutboundEmail("jane@example.com", "Re: Login [#3-ABCDEFGH]", "Try Forgot Password.",
                "<ours@test.example>", "<m1@x>", "<m1@x>", true), "Support <support@test.example>",
                "support@test.example");

        server.verify();
    }
}
