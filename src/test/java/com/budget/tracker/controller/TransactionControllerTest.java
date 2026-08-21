package com.budget.tracker.controller;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Transaction;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.payload.request.TransactionRequest;
import com.budget.tracker.payload.response.ExpenditureSummaryResponse;
import com.budget.tracker.security.UserDetailsImpl;
import com.budget.tracker.service.ExpenditureSummaryService;
import com.budget.tracker.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private ExpenditureSummaryService expenditureSummaryService;

    @Autowired
    private ObjectMapper objectMapper;

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
    void shouldCreateTransaction() throws Exception {
        TransactionRequest req = new TransactionRequest();
        req.setAccountId(UUID.randomUUID());
        req.setAmount(new BigDecimal("50.00"));
        req.setDescription("Lunch");
        req.setType(TransactionType.EXPENSE);
        req.setTransactionDate(OffsetDateTime.now());

        Transaction saved = new Transaction();
        saved.setId(UUID.randomUUID());
        saved.setAmount(new BigDecimal("50.00"));
        saved.setUserId(userId);

        when(transactionService.createTransaction(any(TransactionRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/api/v1/transactions")
                .with(user(userDetails))
                .header("HX-Request", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(50.00));
    }

    @Test
    void shouldUpdateTransaction() throws Exception {
        UUID tid = UUID.randomUUID();
        TransactionRequest req = new TransactionRequest();
        req.setAccountId(UUID.randomUUID());
        req.setAmount(new BigDecimal("75.00"));
        req.setDescription("Dinner");
        req.setType(TransactionType.EXPENSE);
        req.setTransactionDate(OffsetDateTime.now());

        Transaction updated = new Transaction();
        updated.setId(tid);
        updated.setAmount(new BigDecimal("75.00"));
        updated.setDescription("Dinner");
        updated.setUserId(userId);

        when(transactionService.updateTransaction(eq(tid), any(TransactionRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/transactions/" + tid)
                .with(user(userDetails))
                .header("HX-Request", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(75.00))
                .andExpect(jsonPath("$.description").value("Dinner"));
    }

    @Test
    void shouldGetAllTransactions() throws Exception {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setDescription("Salary");

        when(transactionService.getTransactions(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(t)));

        mockMvc.perform(get("/api/v1/transactions")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("Salary"));
    }

    @Test
    void shouldDeleteTransaction() throws Exception {
        UUID tid = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/transactions/" + tid)
                .with(user(userDetails))
                .header("HX-Request", "true"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldGetExpenditureSummary() throws Exception {
        ExpenditureSummaryResponse summary = new ExpenditureSummaryResponse();
        summary.setYesterday(new BigDecimal("1.00"));
        summary.setToday(new BigDecimal("2.00"));
        summary.setLastWeek(new BigDecimal("3.00"));
        summary.setThisWeek(new BigDecimal("4.00"));
        summary.setLastMonth(new BigDecimal("5.00"));
        summary.setThisMonth(new BigDecimal("6.00"));
        when(expenditureSummaryService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/transactions/expenditure-summary")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yesterday").value(1.00))
                .andExpect(jsonPath("$.today").value(2.00))
                .andExpect(jsonPath("$.lastWeek").value(3.00))
                .andExpect(jsonPath("$.thisWeek").value(4.00))
                .andExpect(jsonPath("$.lastMonth").value(5.00))
                .andExpect(jsonPath("$.thisMonth").value(6.00));
    }
}
