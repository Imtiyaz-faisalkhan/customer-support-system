package com.customersupport.backend.controller;

import com.customersupport.backend.enums.TicketStatus;
import com.customersupport.backend.repository.TicketRepository;
import com.customersupport.backend.service.TicketService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        ticketRepository.deleteAll();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"changeme123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        session = (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    @Test
    void metricsWithoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/dashboard/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void metrics_reflectTicketsCreatedToday() throws Exception {
        ticketService.openTicket("a@example.com", "A", "No reply yet", "Hello?", null);
        Long resolvedId = ticketService.openTicket("b@example.com", "B", "Invoice", "Need an invoice", null).id();
        ticketService.reply(resolvedId, "Sent!");
        ticketService.updateStatus(resolvedId, TicketStatus.RESOLVED);
        Long inProgressId = ticketService.openTicket("c@example.com", "C", "Bug", "It broke", null).id();
        ticketService.reply(inProgressId, "Looking into it");

        MvcResult daily = mockMvc.perform(get("/api/dashboard/metrics").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("DAILY"))
                .andExpect(jsonPath("$.totalTickets").value(3))
                .andExpect(jsonPath("$.ticketsOverTime", hasSize(30)))
                .andExpect(jsonPath("$.ticketsOverTime[29].bucketStart").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}")))
                .andExpect(jsonPath("$.from").value(endsWith("Z")))
                .andExpect(jsonPath("$.statusCounts[?(@.status == 'OPEN')].count").value(1))
                .andExpect(jsonPath("$.statusCounts[?(@.status == 'IN_PROGRESS')].count").value(1))
                .andExpect(jsonPath("$.statusCounts[?(@.status == 'RESOLVED')].count").value(1))
                .andExpect(jsonPath("$.categoryCounts[3].category").doesNotExist())
                .andExpect(jsonPath("$.categoryCounts[3].count").value(3))
                .andExpect(jsonPath("$.handled.ai").value(0))
                .andExpect(jsonPath("$.handled.human").value(2))
                .andExpect(jsonPath("$.handled.unhandled").value(1))
                .andExpect(jsonPath("$.avgResponseSeconds").isNumber())
                .andExpect(jsonPath("$.resolution.resolved").value(1))
                .andExpect(jsonPath("$.resolution.total").value(3))
                .andReturn();
        // Sum the buckets rather than asserting "all in today's bucket": that would fail if the run crossed midnight.
        assertThat(bucketTotal(daily)).isEqualTo(3);

        MvcResult weekly = mockMvc.perform(get("/api/dashboard/metrics").param("period", "WEEKLY").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketsOverTime", hasSize(12)))
                .andReturn();
        assertThat(bucketTotal(weekly)).isEqualTo(3);
    }

    private static int bucketTotal(MvcResult result) throws Exception {
        List<Integer> counts = JsonPath.read(result.getResponse().getContentAsString(), "$.ticketsOverTime[*].count");
        return counts.stream().mapToInt(Integer::intValue).sum();
    }

    @Test
    void unknownPeriod_returns400() throws Exception {
        mockMvc.perform(get("/api/dashboard/metrics").param("period", "MONTHLY").session(session))
                .andExpect(status().isBadRequest());
    }
}
