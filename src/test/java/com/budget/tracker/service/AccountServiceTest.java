package com.budget.tracker.service;

import com.budget.tracker.model.Account;
import com.budget.tracker.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountService accountService;

    private UUID userId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        accountService = new AccountService(accountRepository);
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
    }

    @Test
    void createAccount_shouldInitializeBalanceFromInitialBalance() {
        Account account = new Account();
        account.setName("Test Account");
        account.setUserId(userId);
        account.setInitialBalance(new BigDecimal("1000"));

        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        Account savedAccount = accountService.createAccount(account);

        assertEquals(new BigDecimal("1000"), savedAccount.getBalance());
        assertEquals(new BigDecimal("1000"), savedAccount.getInitialBalance());
    }

    @Test
    void updateAccount_shouldAdjustBalanceByInitialBalanceDelta() {
        Account existing = new Account();
        existing.setId(accountId);
        existing.setUserId(userId);
        existing.setInitialBalance(new BigDecimal("1000"));
        existing.setBalance(new BigDecimal("500")); // 1000 initial - 500 expenses

        Account details = new Account();
        details.setInitialBalance(new BigDecimal("1200")); // Increase initial by 200
        details.setName("Updated Name");

        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(existing));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        Account updated = accountService.updateAccount(accountId, userId, details);

        // New balance should be 500 + (1200 - 1000) = 700
        assertEquals(new BigDecimal("700"), updated.getBalance());
        assertEquals(new BigDecimal("1200"), updated.getInitialBalance());
        assertEquals("Updated Name", updated.getName());
    }

    @Test
    void getAccountById_shouldReturnAccount_whenExists() {
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(userId);

        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(account));

        Account foundAccount = accountService.getAccountById(accountId, userId);

        assertNotNull(foundAccount);
        assertEquals(accountId, foundAccount.getId());
    }

    @Test
    void getAccountById_shouldThrowException_whenNotFound() {
        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> accountService.getAccountById(accountId, userId));
    }

    @Test
    void getAllAccountsForUser_shouldReturnAllAccounts() {
        Account account1 = new Account();
        account1.setId(UUID.randomUUID());
        account1.setUserId(userId);
        Account account2 = new Account();
        account2.setId(UUID.randomUUID());
        account2.setUserId(userId);

        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(account1, account2));

        List<Account> accounts = accountService.getAllAccountsForUser(userId);

        assertEquals(2, accounts.size());
    }
}
