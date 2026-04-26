package com.budget.tracker.controller;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.security.UserDetailsImpl;
import com.budget.tracker.service.AccountService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userId;
    private UUID accountId;
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        
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
    void shouldCreateAccount() throws Exception {
        Account account = new Account();
        account.setName("New Account");
        account.setType(AccountType.CASH);
        account.setInitialBalance(new BigDecimal("1000"));

        Account saved = new Account();
        saved.setId(accountId);
        saved.setName("New Account");
        saved.setUserId(userId);

        when(accountService.createAccount(any(Account.class))).thenReturn(saved);

        mockMvc.perform(post("/api/v1/accounts")
                .with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.name").value("New Account"));
    }

    @Test
    void shouldGetAllAccounts() throws Exception {
        Account account = new Account();
        account.setId(accountId);
        account.setName("Test Account");

        when(accountService.getAllAccountsForUser()).thenReturn(List.of(account));

        mockMvc.perform(get("/api/v1/accounts")
                .with(user(userDetails)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(accountId.toString()))
                .andExpect(jsonPath("$[0].name").value("Test Account"));
    }

    @Test
    void shouldGetAccountById() throws Exception {
        Account account = new Account();
        account.setId(accountId);
        account.setName("Test Account");

        when(accountService.getAccountById(accountId)).thenReturn(account);

        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.name").value("Test Account"));
    }

    @Test
    void shouldUpdateAccount() throws Exception {
        Account details = new Account();
        details.setName("Updated Name");

        Account updated = new Account();
        updated.setId(accountId);
        updated.setName("Updated Name");

        when(accountService.updateAccount(eq(accountId), any(Account.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/accounts/" + accountId)
                .with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(details)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    void shouldDeleteAccount() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/" + accountId)
                .with(user(userDetails)))
                .andExpect(status().isNoContent());
    }
}
