package com.budget.tracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:55173,http://localhost:3300",
        "app.security.enabled=false"
})
class CorsConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnAllowOriginHeaderForAllowedOrigin() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Origin", "http://localhost:55173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:55173"));
    }

    @Test
    void shouldReturnAllowOriginForSecondAllowedOrigin() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Origin", "http://localhost:3300"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3300"));
    }

    @Test
    void shouldRejectDisallowedOrigin() throws Exception {
        // Spring's CORS filter returns 403 for disallowed origins — the correct security behavior.
        mockMvc.perform(get("/actuator/health")
                        .header("Origin", "http://evil.com"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void shouldHandlePreflightOptionsRequest() throws Exception {
        mockMvc.perform(options("/actuator/health")
                        .header("Origin", "http://localhost:55173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:55173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void shouldIncludeAllowCredentialsHeader() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("Origin", "http://localhost:55173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void shouldNotReturnCorsHeadersWhenOriginMissing() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
