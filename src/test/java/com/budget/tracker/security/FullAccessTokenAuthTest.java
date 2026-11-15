package com.budget.tracker.security;

import com.budget.tracker.model.ApiTokenScope;
import com.budget.tracker.payload.request.LoginRequest;
import com.budget.tracker.payload.request.SignupRequest;
import com.budget.tracker.service.ApiTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class FullAccessTokenAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiTokenService apiTokenService;

    private UUID userId;
    private String jwt;

    @BeforeEach
    void registerAndLogin() throws Exception {
        String email = "rw-auth-" + UUID.randomUUID() + "@test.com";
        String password = "password123";

        SignupRequest signup = new SignupRequest();
        signup.setEmail(email);
        signup.setPassword(password);
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isOk());

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword(password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        jwt = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        userId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    @Test
    void canReadAndWriteBudgetData() throws Exception {
        String rawToken = apiTokenService.generateToken(userId, ApiTokenScope.FULL_ACCESS, 30);

        mockMvc.perform(post("/api/v1/accounts").header("Authorization", "Bearer " + rawToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Assistant\",\"type\":\"BANK\",\"initialBalance\":100.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/transactions/expenditure-summary").header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isOk());
    }

    @Test
    void cannotReachEndpointsOutsideBudgetData() throws Exception {
        String rawToken = apiTokenService.generateToken(userId, ApiTokenScope.FULL_ACCESS, 30);

        mockMvc.perform(get("/api/v1/api-tokens").header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Full-access API token cannot access this endpoint"));
        mockMvc.perform(post("/api/v1/api-tokens").header("Authorization", "Bearer " + rawToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expiryDays\":30,\"scope\":\"FULL_ACCESS\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/backups/clear").header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/settings").header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/accountsx").header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void jwtCanStillManageTokens() throws Exception {
        apiTokenService.generateToken(userId, ApiTokenScope.FULL_ACCESS, 30);

        mockMvc.perform(get("/api/v1/api-tokens").param("scope", "FULL_ACCESS").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenPreview").value(org.hamcrest.Matchers.startsWith("bt_rw_")));
        mockMvc.perform(get("/api/v1/api-tokens").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());
    }
}
