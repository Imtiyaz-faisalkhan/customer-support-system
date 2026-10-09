package com.customersupport.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The UI is opened on localhost and on the public ngrok URL; both must be able to log in, nothing else. */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:5173, https://my-tunnel.ngrok-free.dev")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsIntegrationTest {

    private static final String LOGIN = "{\"username\":\"admin\",\"password\":\"changeme123\"}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginWorksFromEveryConfiguredOrigin() throws Exception {
        for (String origin : new String[] {"http://localhost:5173", "https://my-tunnel.ngrok-free.dev"}) {
            mockMvc.perform(post("/api/auth/login").header("Origin", origin)
                            .contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void otherOriginsAreRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login").header("Origin", "https://evil.example.com")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isForbidden());
    }
}
