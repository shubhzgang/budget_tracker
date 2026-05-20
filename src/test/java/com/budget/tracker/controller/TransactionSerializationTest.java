package com.budget.tracker.controller;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.repository.AccountRepository;
import com.budget.tracker.repository.TransactionRepository;
import com.budget.tracker.security.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that verify Transaction entity serialization when the Hibernate session is closed.
 *
 * With open-in-view=false (the production best practice), lazy-loaded associations are not
 * initialized when Jackson serializes the entity. Without @JsonIgnoreProperties on Transaction,
 * the toAccount field serializes as null — producing "Transfer to undefined" in the UI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
public class TransactionSerializationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

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
    void transferToAccountShouldSerializeWhenSessionClosed() throws Exception {
        // Create two accounts
        Account fromAccount = new Account();
        fromAccount.setName("Source Account");
        fromAccount.setType(AccountType.CASH);
        fromAccount.setInitialBalance(BigDecimal.valueOf(1000));
        fromAccount.setBalance(BigDecimal.valueOf(1000));
        fromAccount.setUserId(userId);
        accountRepository.save(fromAccount);

        Account toAccount = new Account();
        toAccount.setName("Dest Account");
        toAccount.setType(AccountType.BANK);
        toAccount.setInitialBalance(BigDecimal.valueOf(0));
        toAccount.setBalance(BigDecimal.valueOf(0));
        toAccount.setUserId(userId);
        accountRepository.save(toAccount);

        // Create a transfer transaction directly (bypass service to ensure clean state)
        com.budget.tracker.model.Transaction transfer = new com.budget.tracker.model.Transaction();
        transfer.setAmount(new BigDecimal("200.00"));
        transfer.setTransactionDate(java.time.OffsetDateTime.now());
        transfer.setDescription("Monthly savings transfer");
        transfer.setType(com.budget.tracker.model.TransactionType.TRANSFER);
        transfer.setAccount(fromAccount);
        transfer.setToAccount(toAccount);
        transfer.setUserId(userId);
        transactionRepository.save(transfer);

        // Fetch transactions — with open-in-view=false, the session is closed before serialization.
        // toAccount should still be present in the JSON response.
        mockMvc.perform(get("/api/v1/transactions")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$.content[0].toAccount").isNotEmpty())
                .andExpect(jsonPath("$.content[0].toAccount.id").value(toAccount.getId().toString()))
                .andExpect(jsonPath("$.content[0].toAccount.name").value("Dest Account"));
    }
}
