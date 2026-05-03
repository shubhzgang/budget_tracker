package com.budget.tracker.controller;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.Transaction;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.payload.request.TransferRequest;
import com.budget.tracker.security.UserDetailsImpl;
import com.budget.tracker.service.AccountService;
import com.budget.tracker.service.CategoryService;
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
import org.springframework.data.domain.PageRequest;
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
    private AccountService accountService;

    @MockBean
    private CategoryService categoryService;

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
        Transaction transaction = new Transaction();
        transaction.setAmount(new BigDecimal("50.00"));
        transaction.setDescription("Lunch");
        transaction.setType(TransactionType.EXPENSE);

        Transaction saved = new Transaction();
        saved.setId(UUID.randomUUID());
        saved.setAmount(new BigDecimal("50.00"));
        saved.setUserId(userId);

        when(transactionService.createTransaction(any(Transaction.class))).thenReturn(saved);

        mockMvc.perform(post("/api/v1/transactions")
                .with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transaction)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(50.00));
    }

    @Test
    void shouldCreateTransfer() throws Exception {
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(fromId);
        request.setToAccountId(toId);
        request.setAmount(new BigDecimal("100.00"));
        request.setDescription("Transfer to savings");
        request.setTransactionDate(OffsetDateTime.now());

        Account fromAccount = new Account();
        fromAccount.setId(fromId);
        Account toAccount = new Account();
        toAccount.setId(toId);

        Transaction source = new Transaction();
        source.setId(UUID.randomUUID());
        source.setAmount(new BigDecimal("100.00"));

        when(accountService.getAccountById(fromId)).thenReturn(fromAccount);
        when(accountService.getAccountById(toId)).thenReturn(toAccount);
        when(transactionService.createTransfer(any(Transaction.class), eq(toAccount))).thenReturn(source);

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100.00));
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
                .with(user(userDetails)))
                .andExpect(status().isNoContent());
    }
}
