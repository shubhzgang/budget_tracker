package com.budget.tracker.service;

import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
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
    void createAccount_shouldSaveAccount() {
        Account account = new Account();
        account.setName("Test Account");
        account.setUserId(userId);

        when(accountRepository.save(any(Account.class))).thenReturn(account);

        Account savedAccount = accountService.createAccount(account);

        assertNotNull(savedAccount);
        assertEquals("Test Account", savedAccount.getName());
        verify(accountRepository, times(1)).save(account);
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
