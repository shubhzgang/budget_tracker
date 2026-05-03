package com.budget.tracker.controller;

import com.budget.tracker.model.TransactionType;
import com.budget.tracker.model.UserPreference;
import com.budget.tracker.payload.request.UserPreferenceRequest;
import com.budget.tracker.security.UserDetailsImpl;
import com.budget.tracker.service.UserPreferenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserPreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserPreferenceService userPreferenceService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userId;
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userDetails = new UserDetailsImpl(userId, "test@example.com", "password");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void shouldGetPreferences() throws Exception {
        UserPreference prefs = new UserPreference();
        prefs.setUserId(userId);
        prefs.setDefaultTransactionType(TransactionType.EXPENSE);

        when(userPreferenceService.getPreferences(userId)).thenReturn(prefs);

        mockMvc.perform(get("/api/v1/preferences")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.defaultTransactionType").value("EXPENSE"));
    }

    @Test
    void shouldUpdatePreferences() throws Exception {
        UserPreferenceRequest request = new UserPreferenceRequest();
        request.setDefaultTransactionType(TransactionType.INCOME);

        UserPreference saved = new UserPreference();
        saved.setUserId(userId);
        saved.setDefaultTransactionType(TransactionType.INCOME);

        when(userPreferenceService.updatePreferences(eq(userId), any(UserPreference.class))).thenReturn(saved);

        mockMvc.perform(put("/api/v1/preferences")
                .with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultTransactionType").value("INCOME"));
    }
}
