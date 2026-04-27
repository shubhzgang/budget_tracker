package com.budget.tracker.controller;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Label;
import com.budget.tracker.security.UserDetailsImpl;
import com.budget.tracker.service.LabelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LabelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LabelService labelService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userId;
    private UUID labelId;
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        labelId = UUID.randomUUID();

        // Mock Security Context
        userDetails = new UserDetailsImpl(userId, "test@test.com", "password");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Mock AuthContext
        AuthContext.setUserId(userId);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateLabel() throws Exception {
        Label label = new Label();
        label.setName("NEEDS");

        Label saved = new Label();
        saved.setId(labelId);
        saved.setName("NEEDS");
        saved.setUserId(userId);

        when(labelService.createLabel(any(Label.class))).thenReturn(saved);

        mockMvc.perform(post("/api/v1/labels")
                .with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(label)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(labelId.toString()))
                .andExpect(jsonPath("$.name").value("NEEDS"));
    }

    @Test
    void shouldGetAllLabels() throws Exception {
        Label label = new Label();
        label.setId(labelId);
        label.setName("WANTS");

        when(labelService.getAllLabelsForUser()).thenReturn(List.of(label));

        mockMvc.perform(get("/api/v1/labels")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(labelId.toString()))
                .andExpect(jsonPath("$[0].name").value("WANTS"));
    }

    @Test
    void shouldGetLabelById() throws Exception {
        Label label = new Label();
        label.setId(labelId);
        label.setName("SAVINGS");

        when(labelService.getLabelById(labelId)).thenReturn(label);

        mockMvc.perform(get("/api/v1/labels/" + labelId)
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(labelId.toString()))
                .andExpect(jsonPath("$.name").value("SAVINGS"));
    }

    @Test
    void shouldUpdateLabel() throws Exception {
        Label details = new Label();
        details.setName("Updated Label");

        Label updated = new Label();
        updated.setId(labelId);
        updated.setName("Updated Label");

        when(labelService.updateLabel(eq(labelId), any(Label.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/labels/" + labelId)
                .with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(details)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Label"));
    }

    @Test
    void shouldDeleteLabel() throws Exception {
        mockMvc.perform(delete("/api/v1/labels/" + labelId)
                .with(user(userDetails)))
                .andExpect(status().isNoContent());
    }
}
