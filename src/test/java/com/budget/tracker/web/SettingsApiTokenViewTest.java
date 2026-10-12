package com.budget.tracker.web;

import com.budget.tracker.payload.request.LoginRequest;
import com.budget.tracker.payload.request.SignupRequest;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class SettingsApiTokenViewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void registerAndLogin() throws Exception {
        String email = "api-token-view-" + UUID.randomUUID() + "@test.com";
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
        token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void settingsPageShowsApiTab() throws Exception {
        mockMvc.perform(get("/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-tab=\"api\"")))
                .andExpect(content().string(containsString("data-panel-src=\"api\"")));
    }

    @Test
    void tokenPanelShowsGenerateFormWhenNoToken() throws Exception {
        mockMvc.perform(get("/settings/api-token")
                .header("Authorization", "Bearer " + token)
                .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No Active Token")))
                .andExpect(content().string(containsString("Generate Token")));
    }

    @Test
    void generateShowsRawTokenOnceThenMetadataOnly() throws Exception {
        MvcResult generated = mockMvc.perform(post("/settings/api-token/generate")
                .header("Authorization", "Bearer " + token)
                .header("HX-Request", "true")
                .param("expiryDays", "90"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("bt_ro_")))
                .andExpect(content().string(containsString("Current Token")))
                .andReturn();

        String body = generated.getResponse().getContentAsString();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("id=\"new-api-token\"[^>]*>([^<]+)<")
                .matcher(body);
        assertTrue(matcher.find(), "Raw token should be rendered inside #new-api-token");
        String rawToken = matcher.group(1);

        mockMvc.perform(get("/settings/api-token")
                .header("Authorization", "Bearer " + token)
                .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Current Token")))
                .andExpect(content().string(containsString(rawToken.substring(0, 10))))
                .andExpect(content().string(not(containsString(rawToken))));
    }

    @Test
    void revokeReturnsToGenerateForm() throws Exception {
        mockMvc.perform(post("/settings/api-token/generate")
                .header("Authorization", "Bearer " + token)
                .header("HX-Request", "true")
                .param("expiryDays", "30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Current Token")));

        mockMvc.perform(delete("/settings/api-token")
                .header("Authorization", "Bearer " + token)
                .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No Active Token")));
    }

    @Test
    void invalidExpiryDaysShowsError() throws Exception {
        mockMvc.perform(post("/settings/api-token/generate")
                .header("Authorization", "Bearer " + token)
                .header("HX-Request", "true")
                .param("expiryDays", "45"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("expiryDays must be one of")));
    }
}
