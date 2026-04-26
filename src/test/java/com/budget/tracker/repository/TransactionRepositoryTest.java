package com.budget.tracker.repository;

import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.model.Transaction;
import com.budget.tracker.model.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void shouldSaveAndFindTransaction() {
        Account account = new Account();
        account.setName("Test Account");
        account.setType(AccountType.CASH);
        account.setInitialBalance(BigDecimal.valueOf(100.00));
        account.setBalance(BigDecimal.valueOf(100.00));
        account.setUserId(UUID.randomUUID());
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setAmount(BigDecimal.valueOf(50.00));
        transaction.setTransactionDate(OffsetDateTime.now());
        transaction.setDescription("Test Transaction");
        transaction.setType(TransactionType.EXPENSE);
        transaction.setAccount(account);
        transaction.setUserId(account.getUserId());

        Transaction savedTransaction = transactionRepository.save(transaction);
        Optional<Transaction> foundTransaction = transactionRepository.findById(savedTransaction.getId());

        assertThat(foundTransaction).isPresent();
        assertThat(foundTransaction.get().getAmount()).isEqualByComparingTo("50.00");
        assertThat(foundTransaction.get().getDescription()).isEqualTo("Test Transaction");
        assertThat(foundTransaction.get().getType()).isEqualTo(TransactionType.EXPENSE);
    }

    @Test
    void shouldFindTransactionsByUserId() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        Account account1 = new Account();
        account1.setName("User1 Account");
        account1.setType(AccountType.CASH);
        account1.setInitialBalance(BigDecimal.valueOf(100.00));
        account1.setBalance(BigDecimal.valueOf(100.00));
        account1.setUserId(userId1);
        accountRepository.save(account1);

        Account account2 = new Account();
        account2.setName("User2 Account");
        account2.setType(AccountType.CASH);
        account2.setInitialBalance(BigDecimal.valueOf(200.00));
        account2.setBalance(BigDecimal.valueOf(200.00));
        account2.setUserId(userId2);
        accountRepository.save(account2);

        Transaction transaction1 = new Transaction();
        transaction1.setAmount(BigDecimal.valueOf(10.00));
        transaction1.setTransactionDate(OffsetDateTime.now());
        transaction1.setDescription("User1 Transaction");
        transaction1.setType(TransactionType.EXPENSE);
        transaction1.setAccount(account1);
        transaction1.setUserId(account1.getUserId());
        transactionRepository.save(transaction1);

        Transaction transaction2 = new Transaction();
        transaction2.setAmount(BigDecimal.valueOf(20.00));
        transaction2.setTransactionDate(OffsetDateTime.now());
        transaction2.setDescription("User2 Transaction");
        transaction2.setType(TransactionType.EXPENSE);
        transaction2.setAccount(account2);
        transaction2.setUserId(account2.getUserId());
        transactionRepository.save(transaction2);

        List<Transaction> user1Transactions = transactionRepository.findAllByUserId(userId1);
        List<Transaction> user2Transactions = transactionRepository.findAllByUserId(userId2);

        assertThat(user1Transactions).hasSize(1);
        assertThat(user1Transactions.getFirst().getDescription()).isEqualTo("User1 Transaction");
        assertThat(user2Transactions).hasSize(1);
        assertThat(user2Transactions.getFirst().getDescription()).isEqualTo("User2 Transaction");
    }

    @Test
    void shouldNotFindTransactionsByUserIdIfOwnedByAnotherUser() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        Account account1 = new Account();
        account1.setName("User1 Account");
        account1.setType(AccountType.CASH);
        account1.setInitialBalance(BigDecimal.valueOf(100.00));
        account1.setBalance(BigDecimal.valueOf(100.00));
        account1.setUserId(userId1);
        accountRepository.save(account1);

        Account account2 = new Account();
        account2.setName("User2 Account");
        account2.setType(AccountType.CASH);
        account2.setInitialBalance(BigDecimal.valueOf(200.00));
        account2.setBalance(BigDecimal.valueOf(200.00));
        account2.setUserId(userId2);
        accountRepository.save(account2);

        Transaction transaction1 = new Transaction();
        transaction1.setAmount(BigDecimal.valueOf(10.00));
        transaction1.setTransactionDate(OffsetDateTime.now());
        transaction1.setDescription("User1 Transaction");
        transaction1.setType(TransactionType.EXPENSE);
        transaction1.setAccount(account1);
        transaction1.setUserId(account1.getUserId());
        transactionRepository.save(transaction1);

        List<Transaction> user2Transactions = transactionRepository.findAllByUserId(userId2);

        assertThat(user2Transactions).isEmpty();
    }
}
