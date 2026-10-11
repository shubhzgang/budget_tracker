package com.budget.tracker.security;

import com.budget.tracker.model.ApiToken;
import com.budget.tracker.payload.request.LoginRequest;
import com.budget.tracker.payload.request.SignupRequest;
import com.budget.tracker.repository.ApiTokenRepository;
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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ReadOnlyTokenAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiTokenService apiTokenService;

    @Autowired
    private ApiTokenRepository apiTokenRepository;

    private UUID userId;
    private String jwt;

    @BeforeEach
    void registerAndLogin() throws Exception {
        String email = "ro-auth-" + UUID.randomUUID() + "@test.com";
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
    void expiredTokenIsRejectedWith401() throws Exception {
        String rawToken = apiTokenService.generateToken(userId, 30);

        mockMvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isOk());

        ApiToken apiToken = apiTokenRepository.findByUserId(userId).orElseThrow();
        apiToken.setExpiresAt(OffsetDateTime.now().minusDays(1));
        apiTokenRepository.save(apiToken);

        mockMvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithReadOnlyTokenIsHarmless() throws Exception {
        String rawToken = apiTokenService.generateToken(userId, 30);

        mockMvc.perform(post("/logout").header("Authorization", "Bearer " + rawToken))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + rawToken))
                .andExpect(status().isOk());
    }
}
