package com.budget.tracker.controller;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.payload.response.ActivityResponse;
import com.budget.tracker.security.UserDetailsImpl;
import com.budget.tracker.service.ActivityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActivityService activityService;

    private UUID userId;
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userDetails = new UserDetailsImpl(userId, "test@test.com", "password");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        AuthContext.setUserId(userId);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldGetAllActivity() throws Exception {
        ActivityResponse act1 = new ActivityResponse();
        act1.setId(UUID.randomUUID());
        act1.setKind("TRANSACTION");
        act1.setType("EXPENSE");
        act1.setAmount(new BigDecimal("50.00"));
        act1.setDescription("Dinner");

        ActivityResponse act2 = new ActivityResponse();
        act2.setId(UUID.randomUUID());
        act2.setKind("TRANSFER");
        act2.setType("TRANSFER");
        act2.setFromAmount(new BigDecimal("100.00"));
        act2.setToAmount(new BigDecimal("110.00"));
        act2.setAdjustment(new BigDecimal("10.00"));
        act2.setDescription("CC Payment");

        when(activityService.getActivity(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(act1, act2)));

        mockMvc.perform(get("/api/v1/activity")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].kind").value("TRANSACTION"))
                .andExpect(jsonPath("$.content[0].description").value("Dinner"))
                .andExpect(jsonPath("$.content[1].kind").value("TRANSFER"))
                .andExpect(jsonPath("$.content[1].adjustment").value(10.00));
    }

    @Test
    void shouldFilterByAccountId() throws Exception {
        UUID accountId = UUID.randomUUID();

        ActivityResponse act = new ActivityResponse();
        act.setId(UUID.randomUUID());
        act.setKind("TRANSACTION");
        act.setType("EXPENSE");
        act.setAmount(new BigDecimal("75.00"));
        act.setDescription("Groceries");

        when(activityService.getActivity(any(), any(), eq(accountId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(act)));

        mockMvc.perform(get("/api/v1/activity")
                .param("accountId", accountId.toString())
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("Groceries"));

        verify(activityService).getActivity(any(), any(), eq(accountId), any(), any(), any());
    }
}
