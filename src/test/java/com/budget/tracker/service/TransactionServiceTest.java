package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.model.Category;
import com.budget.tracker.model.Label;
import com.budget.tracker.model.Transaction;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.repository.AccountRepository;
import com.budget.tracker.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    private TransactionService transactionService;

    private UUID userId;
    private UUID transactionId;
    private UUID accountId;
    private UUID destAccountId;

    private Account sourceAccount;
    private Account destAccount;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        transactionService = new TransactionService(transactionRepository, accountRepository);
        userId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        destAccountId = UUID.randomUUID();
        AuthContext.setUserId(userId);

        sourceAccount = new Account();
        sourceAccount.setId(accountId);
        sourceAccount.setUserId(userId);
        sourceAccount.setBalance(new BigDecimal("1000"));
        sourceAccount.setName("My Bank");
        sourceAccount.setType(AccountType.BANK);

        destAccount = new Account();
        destAccount.setId(destAccountId);
        destAccount.setUserId(userId);
        destAccount.setBalance(new BigDecimal("500"));
        destAccount.setName("Savings");
        destAccount.setType(AccountType.BANK);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    // -- createTransaction --

    @Test
    void createTransaction_income_shouldSaveAndIncreaseBalance() {
        Transaction tx = buildTransaction(TransactionType.INCOME, sourceAccount);
        tx.setAmount(new BigDecimal("200"));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sourceAccount));
        when(transactionRepository.save(any())).thenReturn(tx);

        Transaction result = transactionService.createTransaction(tx);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        verify(transactionRepository).save(tx);
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) &&
                acct.getBalance().compareTo(new BigDecimal("1200")) == 0
        ));
    }

    @Test
    void createTransaction_expense_shouldSaveAndDecreaseBalance() {
        Transaction tx = buildTransaction(TransactionType.EXPENSE, sourceAccount);
        tx.setAmount(new BigDecimal("50"));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sourceAccount));
        when(transactionRepository.save(any())).thenReturn(tx);

        Transaction result = transactionService.createTransaction(tx);

        assertNotNull(result);
        verify(transactionRepository).save(tx);
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) &&
                acct.getBalance().compareTo(new BigDecimal("950")) == 0
        ));
    }

    @Test
    void createTransaction_creditCardExpense_shouldIncreaseDebt() {
        sourceAccount.setType(AccountType.CREDIT_CARD);
        sourceAccount.setBalance(new BigDecimal("450"));

        Transaction tx = buildTransaction(TransactionType.EXPENSE, sourceAccount);
        tx.setAmount(new BigDecimal("250"));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sourceAccount));
        when(transactionRepository.save(any())).thenReturn(tx);

        Transaction result = transactionService.createTransaction(tx);

        assertNotNull(result);
        // Debt should INCREASE: 450 + 250 = 700
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) &&
                acct.getBalance().compareTo(new BigDecimal("700")) == 0
        ));
    }

    @Test
    void createTransaction_creditCardIncome_shouldDecreaseDebt() {
        sourceAccount.setType(AccountType.CREDIT_CARD);
        sourceAccount.setBalance(new BigDecimal("700"));

        Transaction tx = buildTransaction(TransactionType.INCOME, sourceAccount);
        tx.setAmount(new BigDecimal("200"));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sourceAccount));
        when(transactionRepository.save(any())).thenReturn(tx);

        Transaction result = transactionService.createTransaction(tx);

        assertNotNull(result);
        // Debt should DECREASE: 700 - 200 = 500
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) &&
                acct.getBalance().compareTo(new BigDecimal("500")) == 0
        ));
    }

    @Test
    void createTransaction_lend_shouldSaveAndDecreaseBalance() {
        Transaction tx = buildTransaction(TransactionType.LEND, sourceAccount);
        tx.setAmount(new BigDecimal("100"));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sourceAccount));
        when(transactionRepository.save(any())).thenReturn(tx);

        Transaction result = transactionService.createTransaction(tx);

        assertNotNull(result);
        verify(transactionRepository).save(tx);
        // LEND decreases balance (sending money away): 1000 - 100 = 900
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) &&
                acct.getBalance().compareTo(new BigDecimal("900")) == 0
        ));
    }

    @Test
    void createTransaction_borrow_shouldSaveAndIncreaseBalance() {
        Transaction tx = buildTransaction(TransactionType.BORROW, sourceAccount);
        tx.setAmount(new BigDecimal("250"));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sourceAccount));
        when(transactionRepository.save(any())).thenReturn(tx);

        Transaction result = transactionService.createTransaction(tx);

        assertNotNull(result);
        verify(transactionRepository).save(tx);
        // BORROW increases balance (getting money): 1000 + 250 = 1250
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) &&
                acct.getBalance().compareTo(new BigDecimal("1250")) == 0
        ));
    }

    @Test
    void createTransaction_transferType_shouldThrow() {
        Transaction tx = buildTransaction(TransactionType.TRANSFER, sourceAccount);
        tx.setAmount(new BigDecimal("100"));

        assertThrows(IllegalArgumentException.class, () -> transactionService.createTransaction(tx));
    }

    // -- getTransactionById --

    @Test
    void getTransactionById_shouldReturn_whenExists() {
        Transaction tx = buildTransaction(TransactionType.INCOME, sourceAccount);
        tx.setId(transactionId);

        when(transactionRepository.findAllByUserId(userId)).thenReturn(List.of(tx));

        Transaction found = transactionService.getTransactionById(transactionId);

        assertNotNull(found);
        assertEquals(transactionId, found.getId());
    }

    // -- updateTransaction --

    @Test
    void updateTransaction_nonTransfer_shouldUpdateFieldsAndBalances() {
        Transaction existing = buildTransaction(TransactionType.INCOME, sourceAccount);
        existing.setId(transactionId);
        existing.setAmount(new BigDecimal("100"));

        Transaction details = buildTransaction(TransactionType.INCOME, sourceAccount);
        details.setAmount(new BigDecimal("150"));
        details.setDescription("Updated");

        when(transactionRepository.findAllByUserId(userId)).thenReturn(List.of(existing));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sourceAccount));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction updated = transactionService.updateTransaction(transactionId, details);

        assertEquals(new BigDecimal("150"), updated.getAmount());
        assertEquals("Updated", updated.getDescription());
        // Revert 100 INCOME (sub 100) -> 900. Apply 150 INCOME (add 150) -> 1050.
        verify(accountRepository, atLeastOnce()).save(argThat(acct ->
                acct.getId().equals(accountId) && acct.getBalance().compareTo(new BigDecimal("1050")) == 0
        ));
    }

    @Test
    void updateTransaction_transferType_shouldUpdateBothBalances() {
        UUID linkedId = UUID.randomUUID();
        Transaction linked = new Transaction();
        linked.setId(linkedId);
        linked.setAmount(new BigDecimal("100"));
        linked.setType(TransactionType.TRANSFER);
        linked.setUserId(userId);
        linked.setAccount(destAccount);
        linked.setLinkedTransferId(transactionId);

        Transaction existing = buildTransaction(TransactionType.TRANSFER, sourceAccount);
        existing.setId(transactionId);
        existing.setAmount(new BigDecimal("100"));
        existing.setLinkedTransferId(linkedId);

        when(transactionRepository.findAllByUserId(userId)).thenReturn(List.of(existing));
        when(transactionRepository.findById(linkedId)).thenReturn(Optional.of(linked));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destAccountId)).thenReturn(Optional.of(destAccount));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction details = buildTransaction(TransactionType.TRANSFER, sourceAccount);
        details.setAmount(new BigDecimal("200"));

        transactionService.updateTransaction(transactionId, details);

        // Source: 1000. Revert sub 100 -> 1100. Apply sub 200 -> 900.
        verify(accountRepository, atLeastOnce()).save(argThat(acct ->
                acct.getId().equals(accountId) && acct.getBalance().compareTo(new BigDecimal("900")) == 0
        ));
        // Dest: 500. Revert add 100 -> 400. Apply add 200 -> 600.
        verify(accountRepository, atLeastOnce()).save(argThat(acct ->
                acct.getId().equals(destAccountId) && acct.getBalance().compareTo(new BigDecimal("600")) == 0
        ));
    }

    // -- deleteTransaction --

    @Test
    void deleteTransaction_nonTransfer_shouldReverseBalance() {
        Transaction tx = buildTransaction(TransactionType.INCOME, sourceAccount);
        tx.setId(transactionId);
        tx.setAmount(new BigDecimal("200"));

        when(transactionRepository.findAllByUserId(userId)).thenReturn(List.of(tx));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sourceAccount));

        transactionService.deleteTransaction(transactionId);

        // INCOME 200. Revert -> sub 200. 1000 - 200 = 800.
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) && acct.getBalance().compareTo(new BigDecimal("800")) == 0
        ));
        verify(transactionRepository).delete(tx);
    }

    @Test
    void deleteTransaction_transferWithLinked_shouldReverseBothBalances() {
        UUID linkedId = UUID.randomUUID();
        Transaction linked = new Transaction();
        linked.setId(linkedId);
        linked.setAmount(new BigDecimal("100"));
        linked.setType(TransactionType.TRANSFER);
        linked.setUserId(userId);
        linked.setAccount(destAccount);
        linked.setLinkedTransferId(transactionId);

        Transaction tx = buildTransaction(TransactionType.TRANSFER, sourceAccount);
        tx.setId(transactionId);
        tx.setAmount(new BigDecimal("100"));
        tx.setLinkedTransferId(linkedId);

        when(transactionRepository.findAllByUserId(userId)).thenReturn(List.of(tx));
        when(transactionRepository.findById(linkedId)).thenReturn(Optional.of(linked));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destAccountId)).thenReturn(Optional.of(destAccount));

        transactionService.deleteTransaction(transactionId);

        // Source: 1000. Revert sub 100 -> add 100 -> 1100.
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) && acct.getBalance().compareTo(new BigDecimal("1100")) == 0
        ));
        // Dest: 500. Revert add 100 -> sub 100 -> 400.
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(destAccountId) && acct.getBalance().compareTo(new BigDecimal("400")) == 0
        ));
        verify(transactionRepository).delete(tx);
        verify(transactionRepository).delete(linked);
    }

    // -- createTransfer --

    @Test
    void createTransfer_shouldCreatePairedTransactions() {
        Transaction sourceTx = buildTransaction(TransactionType.TRANSFER, sourceAccount);
        sourceTx.setAmount(new BigDecimal("100"));
        sourceTx.setUserId(userId);

        when(accountRepository.findById(destAccountId)).thenReturn(Optional.of(destAccount));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(sourceAccount));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction result = transactionService.createTransfer(sourceTx, destAccount);

        assertNotNull(result);
        assertNotNull(result.getLinkedTransferId());
        verify(transactionRepository, times(4)).save(any(Transaction.class));
        // Source: 1000 - 100 = 900.
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) && acct.getBalance().compareTo(new BigDecimal("900")) == 0
        ));
        // Dest: 500 + 100 = 600.
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(destAccountId) && acct.getBalance().compareTo(new BigDecimal("600")) == 0
        ));
    }

    // -- list methods --

    @Test
    void getAllTransactionsForUser_shouldReturnAll() {
        Transaction tx1 = buildTransaction(TransactionType.INCOME, sourceAccount);
        Transaction tx2 = buildTransaction(TransactionType.EXPENSE, sourceAccount);
        when(transactionRepository.findAllByUserId(userId)).thenReturn(List.of(tx1, tx2));

        List<Transaction> result = transactionService.getAllTransactionsForUser();

        assertEquals(2, result.size());
    }

    @Test
    void getTransactionsForAccount_shouldReturnFiltered() {
        Transaction tx = buildTransaction(TransactionType.EXPENSE, sourceAccount);
        when(transactionRepository.findAllByAccountIdAndUserId(accountId, userId)).thenReturn(List.of(tx));

        List<Transaction> result = transactionService.getTransactionsForAccount(accountId);

        assertEquals(1, result.size());
    }

    // -- helpers --

    private Transaction buildTransaction(TransactionType type, Account account) {
        Transaction tx = new Transaction();
        tx.setType(type);
        tx.setAccount(account);
        tx.setUserId(account.getUserId());
        tx.setDescription("Test");
        tx.setTransactionDate(OffsetDateTime.now());
        tx.setCategory(new Category());
        tx.setLabel(new Label());
        return tx;
    }
}
