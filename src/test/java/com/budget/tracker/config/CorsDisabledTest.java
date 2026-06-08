package com.budget.tracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies CORS behavior when no allowed-origins are configured (default).
 * No @TestPropertySource for CORS — uses default (blank) app.cors.allowed-origins.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldNotReturnCorsHeadersWhenCorsNotConfigured() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Origin", "http://localhost:55173"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void shouldNotReturnCredentialsHeaderWhenCorsNotConfigured() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Origin", "http://localhost:55173"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }
}
