package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.repository.AccountRepository;
import org.junit.jupiter.api.AfterEach;
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
        AuthContext.setUserId(userId);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void createAccount_shouldInitializeBalanceFromInitialBalance() {
        Account account = new Account();
        account.setName("Test Account");
        account.setInitialBalance(new BigDecimal("1000"));

        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        Account savedAccount = accountService.createAccount(account);

        assertEquals(userId, savedAccount.getUserId());
        assertEquals(new BigDecimal("1000"), savedAccount.getBalance());
        assertEquals(new BigDecimal("1000"), savedAccount.getInitialBalance());
    }

    @Test
    void updateAccount_shouldAdjustBalanceByInitialBalanceDelta() {
        Account existing = new Account();
        existing.setId(accountId);
        existing.setUserId(userId);
        existing.setType(AccountType.BANK);
        existing.setInitialBalance(new BigDecimal("1000"));
        existing.setBalance(new BigDecimal("500")); // 1000 initial - 500 expenses

        Account details = new Account();
        details.setInitialBalance(new BigDecimal("1200")); // Increase initial by 200
        details.setName("Updated Name");

        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(existing));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        Account updated = accountService.updateAccount(accountId, details);

        // New balance should be 500 + (1200 - 1000) = 700
        assertEquals(new BigDecimal("700"), updated.getBalance());
        assertEquals(new BigDecimal("1200"), updated.getInitialBalance());
        assertEquals("Updated Name", updated.getName());
    }

    @Test
    void updateAccount_creditCard_shouldAdjustDebtByInitialBalanceDelta() {
        Account existing = new Account();
        existing.setId(accountId);
        existing.setUserId(userId);
        existing.setType(AccountType.CREDIT_CARD);
        existing.setInitialBalance(new BigDecimal("1000"));
        existing.setBalance(new BigDecimal("1500")); // 1000 initial + 500 expenses

        Account details = new Account();
        details.setInitialBalance(new BigDecimal("1200")); // Increase initial by 200
        details.setName("Updated Name");

        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(existing));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        Account updated = accountService.updateAccount(accountId, details);

        // Debt should increase: 1500 + (1200 - 1000) = 1700
        assertEquals(new BigDecimal("1700"), updated.getBalance());
        assertEquals(new BigDecimal("1200"), updated.getInitialBalance());
    }

    @Test
    void getAccountById_shouldReturnAccount_whenExists() {
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(userId);

        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(account));

        Account found = accountService.getAccountById(accountId);

        assertNotNull(found);
        assertEquals(accountId, found.getId());
    }

    @Test
    void getAccountById_shouldThrow_whenNotFound() {
        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> accountService.getAccountById(accountId));
    }

    @Test
    void getAllAccountsForUser_shouldReturnAll() {
        Account account1 = new Account();
        account1.setUserId(userId);
        Account account2 = new Account();
        account2.setUserId(userId);

        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(account1, account2));

        List<Account> accounts = accountService.getAllAccountsForUser();

        assertEquals(2, accounts.size());
    }

    @Test
    void calculateAvailableCredit_shouldWork() {
        Account account = new Account();
        account.setId(accountId);
        account.setUserId(userId);
        account.setType(AccountType.CREDIT_CARD);
        account.setCreditLimit(new BigDecimal("5000"));
        account.setBalance(new BigDecimal("1000"));

        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(account));

        BigDecimal available = accountService.calculateAvailableCredit(accountId);

        assertEquals(new BigDecimal("4000"), available);
    }
}
