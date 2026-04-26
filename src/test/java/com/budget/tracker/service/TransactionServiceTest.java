package com.budget.tracker.service;

import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.model.Category;
import com.budget.tracker.model.Label;
import com.budget.tracker.model.Transaction;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.repository.AccountRepository;
import com.budget.tracker.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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

    // -- createTransaction --

    @Test
    void createTransaction_income_shouldSaveAndIncreaseBalance() {
        Transaction tx = buildTransaction(TransactionType.INCOME, sourceAccount);
        tx.setAmount(new BigDecimal("200"));

        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(sourceAccount, destAccount));
        when(transactionRepository.save(any())).thenReturn(tx);

        Transaction result = transactionService.createTransaction(tx);

        assertNotNull(result);
        verify(transactionRepository).save(tx);
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) &&
                acct.getBalance().equals(new BigDecimal("1200"))
        ));
    }

    @Test
    void createTransaction_expense_shouldSaveAndDecreaseBalance() {
        Transaction tx = buildTransaction(TransactionType.EXPENSE, sourceAccount);
        tx.setAmount(new BigDecimal("50"));

        BigDecimal[] capturedBalance = new BigDecimal[1];
        UUID[] capturedAccountId = new UUID[1];
        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(sourceAccount, destAccount));
        when(transactionRepository.save(any())).thenReturn(tx);

        Transaction result = transactionService.createTransaction(tx);

        assertNotNull(result);
        verify(transactionRepository).save(tx);
        verify(accountRepository).save(argThat(acct -> {
            capturedAccountId[0] = acct.getId();
            capturedBalance[0] = acct.getBalance();
            return acct.getId() != null;
        }));
        assertEquals(accountId, capturedAccountId[0]);
        assertEquals(new BigDecimal("950"), capturedBalance[0]);
    }

    // -- getTransactionById --

    @Test
    void getTransactionById_shouldReturn_whenExists() {
        Transaction tx = buildTransaction(TransactionType.INCOME, sourceAccount);
        tx.setId(transactionId);

        when(transactionRepository.findAllByUserId(userId)).thenReturn(List.of(tx));

        Transaction found = transactionService.getTransactionById(transactionId, userId);

        assertNotNull(found);
        assertEquals(transactionId, found.getId());
    }

    @Test
    void getTransactionById_shouldThrow_whenNotFound() {
        when(transactionRepository.findAllByUserId(userId)).thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> transactionService.getTransactionById(transactionId, userId));
    }

    // -- getAllTransactionsForUser --

    @Test
    void getAllTransactionsForUser_shouldReturnAll() {
        Transaction tx1 = buildTransaction(TransactionType.INCOME, sourceAccount);
        Transaction tx2 = buildTransaction(TransactionType.EXPENSE, sourceAccount);
        when(transactionRepository.findAllByUserId(userId)).thenReturn(List.of(tx1, tx2));

        List<Transaction> result = transactionService.getAllTransactionsForUser(userId);

        assertEquals(2, result.size());
    }

    // -- getTransactionsForAccount --

    @Test
    void getTransactionsForAccount_shouldReturnFiltered() {
        Transaction tx = buildTransaction(TransactionType.EXPENSE, sourceAccount);
        when(transactionRepository.findAllByAccountIdAndUserId(accountId, userId)).thenReturn(List.of(tx));

        List<Transaction> result = transactionService.getTransactionsForAccount(accountId, userId);

        assertEquals(1, result.size());
    }

    // -- updateTransaction --

    @Test
    void updateTransaction_nonTransfer_shouldUpdateFields() {
        Transaction existing = buildTransaction(TransactionType.INCOME, sourceAccount);
        existing.setId(transactionId);
        existing.setAmount(new BigDecimal("100"));

        Transaction details = buildTransaction(TransactionType.INCOME, sourceAccount);
        details.setAmount(new BigDecimal("150"));
        details.setDescription("Updated");

        when(transactionRepository.findAllByUserId(userId)).thenReturn(List.of(existing));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction updated = transactionService.updateTransaction(transactionId, userId, details);

        assertEquals(new BigDecimal("150"), updated.getAmount());
        assertEquals("Updated", updated.getDescription());
        verify(transactionRepository).save(existing);
    }

    @Test
    void updateTransaction_transferType_shouldUpdateBothBalances() {
        UUID linkedId = UUID.randomUUID();
        Transaction linked = new Transaction();
        linked.setId(UUID.randomUUID());
        linked.setAmount(new BigDecimal("100"));
        linked.setType(TransactionType.TRANSFER);
        linked.setUserId(userId);
        linked.setAccount(destAccount);
        linked.setLinkedTransferId(linkedId);

        Transaction existing = buildTransaction(TransactionType.TRANSFER, sourceAccount);
        existing.setId(transactionId);
        existing.setAmount(new BigDecimal("100"));
        existing.setLinkedTransferId(linkedId);

        when(transactionRepository.findAllByUserId(userId))
                .thenReturn(List.of(existing, linked))
                .thenReturn(List.of(existing, linked));
        when(accountRepository.findAllByUserId(userId))
                .thenReturn(List.of(sourceAccount, destAccount))
                .thenReturn(List.of(sourceAccount, destAccount));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction details = buildTransaction(TransactionType.TRANSFER, sourceAccount);
        details.setAmount(new BigDecimal("200"));

        transactionService.updateTransaction(transactionId, userId, details);

        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) && acct.getBalance().equals(new BigDecimal("800"))
        ));
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(destAccountId) && acct.getBalance().equals(new BigDecimal("300"))
        ));
    }

    @Test
    void updateTransaction_transferTypeWithoutLinked_shouldReverseOriginalBalance() {
        Transaction existing = buildTransaction(TransactionType.LEND, sourceAccount);
        existing.setId(transactionId);
        existing.setAmount(new BigDecimal("100"));
        existing.setLinkedTransferId(null);

        when(transactionRepository.findAllByUserId(userId)).thenReturn(List.of(existing));
        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(sourceAccount, destAccount));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Transaction details = buildTransaction(TransactionType.LEND, sourceAccount);
        details.setAmount(new BigDecimal("50"));

        transactionService.updateTransaction(transactionId, userId, details);

        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) && acct.getBalance().equals(new BigDecimal("1050"))
        ));
    }

    // -- deleteTransaction --

    @Test
    void deleteTransaction_nonTransfer_shouldReverseBalance() {
        Transaction tx = buildTransaction(TransactionType.INCOME, sourceAccount);
        tx.setId(transactionId);
        tx.setAmount(new BigDecimal("200"));

        when(transactionRepository.findAllByUserId(userId)).thenReturn(List.of(tx));
        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(sourceAccount, destAccount));

        transactionService.deleteTransaction(transactionId, userId);

        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) && acct.getBalance().equals(new BigDecimal("800"))
        ));
        verify(transactionRepository).delete(tx);
    }

    @Test
    void deleteTransaction_transferWithLinked_shouldReverseBothBalances() {
        UUID linkedId = UUID.randomUUID();
        Transaction linked = new Transaction();
        linked.setId(UUID.randomUUID());
        linked.setAmount(new BigDecimal("100"));
        linked.setType(TransactionType.TRANSFER);
        linked.setUserId(userId);
        linked.setAccount(destAccount);
        linked.setLinkedTransferId(linkedId);

        Transaction tx = buildTransaction(TransactionType.TRANSFER, sourceAccount);
        tx.setId(transactionId);
        tx.setAmount(new BigDecimal("100"));
        tx.setLinkedTransferId(linkedId);

        when(transactionRepository.findAllByUserId(userId))
                .thenReturn(List.of(tx))
                .thenReturn(List.of(tx, linked));
        when(accountRepository.findAllByUserId(userId))
                .thenReturn(List.of(sourceAccount, destAccount))
                .thenReturn(List.of(sourceAccount, destAccount));

        transactionService.deleteTransaction(transactionId, userId);

        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) && acct.getBalance().equals(new BigDecimal("900"))
        ));
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(destAccountId) && acct.getBalance().equals(new BigDecimal("400"))
        ));
        verify(transactionRepository).delete(tx);
    }

    // -- createTransfer --

    @Test
    void createTransfer_shouldCreatePairedTransactions() {
        UUID linkedId = UUID.randomUUID();
        Transaction sourceTx = buildTransaction(TransactionType.TRANSFER, sourceAccount);
        sourceTx.setAmount(new BigDecimal("100"));
        sourceTx.setUserId(userId);
        sourceTx.setTransactionDate(OffsetDateTime.now());
        sourceTx.setId(UUID.randomUUID());
        sourceTx.setLinkedTransferId(linkedId);

        Transaction destTx = buildTransaction(TransactionType.TRANSFER, destAccount);
        destTx.setAmount(new BigDecimal("100"));
        destTx.setUserId(userId);
        destTx.setTransactionDate(sourceTx.getTransactionDate());
        destTx.setId(UUID.randomUUID());
        destTx.setLinkedTransferId(linkedId);

        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(sourceAccount, destAccount));
        when(transactionRepository.save(any()))
                .thenReturn(sourceTx)
                .thenReturn(destTx)
                .thenReturn(sourceTx);

        Transaction result = transactionService.createTransfer(sourceTx, destAccount);

        assertNotNull(result);
        assertNotNull(result.getLinkedTransferId());
        verify(transactionRepository, times(3)).save(any(Transaction.class));
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) && acct.getBalance().equals(new BigDecimal("900"))
        ));
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(destAccountId) && acct.getBalance().equals(new BigDecimal("600"))
        ));
    }

    @Test
    void createTransfer_sameAccount_shouldThrow() {
        Transaction sourceTx = buildTransaction(TransactionType.TRANSFER, sourceAccount);
        sourceTx.setAmount(new BigDecimal("100"));
        sourceTx.setUserId(userId);

        assertThrows(IllegalArgumentException.class, () -> transactionService.createTransfer(sourceTx, sourceAccount));
    }

    @Test
    void createTransfer_nullAmount_shouldThrow() {
        Transaction sourceTx = buildTransaction(TransactionType.TRANSFER, sourceAccount);
        sourceTx.setUserId(userId);

        assertThrows(IllegalArgumentException.class, () -> transactionService.createTransfer(sourceTx, destAccount));
    }

    @Test
    void createTransfer_wrongType_shouldThrow() {
        Transaction sourceTx = buildTransaction(TransactionType.INCOME, sourceAccount);
        sourceTx.setAmount(new BigDecimal("100"));
        sourceTx.setUserId(userId);

        assertThrows(IllegalArgumentException.class, () -> transactionService.createTransfer(sourceTx, destAccount));
    }

    @Test
    void createTransfer_destNotOwnedByUser_shouldThrow() {
        Transaction sourceTx = buildTransaction(TransactionType.TRANSFER, sourceAccount);
        sourceTx.setAmount(new BigDecimal("100"));
        sourceTx.setUserId(userId);

        Account strangerAccount = new Account();
        strangerAccount.setId(destAccountId);
        strangerAccount.setUserId(UUID.randomUUID());

        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(sourceAccount, strangerAccount));

        assertThrows(RuntimeException.class, () -> transactionService.createTransfer(sourceTx, destAccount));
    }

    @Test
    void createTransfer_lend_shouldWork() {
        Account friendAccount = new Account();
        friendAccount.setId(destAccountId);
        friendAccount.setUserId(userId);
        friendAccount.setBalance(new BigDecimal("0"));
        friendAccount.setName("Alice");
        friendAccount.setType(AccountType.FRIEND_LENDING);

        UUID linkedId = UUID.randomUUID();
        Transaction sourceTx = buildTransaction(TransactionType.LEND, sourceAccount);
        sourceTx.setAmount(new BigDecimal("50"));
        sourceTx.setUserId(userId);
        sourceTx.setId(UUID.randomUUID());
        sourceTx.setLinkedTransferId(linkedId);

        Transaction destTx = buildTransaction(TransactionType.LEND, friendAccount);
        destTx.setAmount(new BigDecimal("50"));
        destTx.setUserId(userId);
        destTx.setId(UUID.randomUUID());
        destTx.setLinkedTransferId(linkedId);

        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(sourceAccount, friendAccount));
        when(transactionRepository.save(any()))
                .thenReturn(sourceTx)
                .thenReturn(destTx)
                .thenReturn(sourceTx);

        Transaction result = transactionService.createTransfer(sourceTx, friendAccount);

        assertNotNull(result);
        verify(transactionRepository, times(3)).save(any(Transaction.class));
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) && acct.getBalance().equals(new BigDecimal("950"))
        ));
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(destAccountId) && acct.getBalance().equals(new BigDecimal("50"))
        ));
    }

    @Test
    void createTransfer_borrow_shouldWork() {
        Account friendAccount = new Account();
        friendAccount.setId(destAccountId);
        friendAccount.setUserId(userId);
        friendAccount.setBalance(new BigDecimal("100"));
        friendAccount.setName("Alice");
        friendAccount.setType(AccountType.FRIEND_LENDING);

        UUID linkedId = UUID.randomUUID();
        Transaction sourceTx = buildTransaction(TransactionType.BORROW, sourceAccount);
        sourceTx.setAmount(new BigDecimal("30"));
        sourceTx.setUserId(userId);
        sourceTx.setId(UUID.randomUUID());
        sourceTx.setLinkedTransferId(linkedId);

        Transaction destTx = buildTransaction(TransactionType.BORROW, friendAccount);
        destTx.setAmount(new BigDecimal("30"));
        destTx.setUserId(userId);
        destTx.setId(UUID.randomUUID());
        destTx.setLinkedTransferId(linkedId);

        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(sourceAccount, friendAccount));
        when(transactionRepository.save(any()))
                .thenReturn(sourceTx)
                .thenReturn(destTx)
                .thenReturn(sourceTx);

        Transaction result = transactionService.createTransfer(sourceTx, friendAccount);

        assertNotNull(result);
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(accountId) && acct.getBalance().equals(new BigDecimal("1030"))
        ));
        verify(accountRepository).save(argThat(acct ->
                acct.getId().equals(destAccountId) && acct.getBalance().equals(new BigDecimal("70"))
        ));
    }

    @Test
    void createTransfer_differentUsers_shouldThrow() {
        Transaction sourceTx = buildTransaction(TransactionType.TRANSFER, sourceAccount);
        sourceTx.setAmount(new BigDecimal("100"));
        sourceTx.setUserId(UUID.randomUUID());

        when(accountRepository.findAllByUserId(sourceTx.getUserId())).thenReturn(List.of(sourceAccount));

        assertThrows(RuntimeException.class, () -> transactionService.createTransfer(sourceTx, destAccount));
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