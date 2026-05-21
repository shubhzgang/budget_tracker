package com.budget.tracker.repository;

import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.model.Transfer;
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
class TransferRepositoryTest {

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void shouldSaveAndFindTransfer() {
        UUID userId = UUID.randomUUID();
        Account fromAccount = new Account();
        fromAccount.setName("From Account");
        fromAccount.setType(AccountType.BANK);
        fromAccount.setInitialBalance(BigDecimal.valueOf(1000.00));
        fromAccount.setBalance(BigDecimal.valueOf(1000.00));
        fromAccount.setUserId(userId);
        accountRepository.save(fromAccount);

        Account toAccount = new Account();
        toAccount.setName("To Account");
        toAccount.setType(AccountType.BANK);
        toAccount.setInitialBalance(BigDecimal.valueOf(500.00));
        toAccount.setBalance(BigDecimal.valueOf(500.00));
        toAccount.setUserId(userId);
        accountRepository.save(toAccount);

        Transfer transfer = new Transfer();
        transfer.setFromAmount(BigDecimal.valueOf(100.00));
        transfer.setToAmount(BigDecimal.valueOf(100.00));
        transfer.setAdjustment(BigDecimal.ZERO);
        transfer.setTransactionDate(OffsetDateTime.now());
        transfer.setDescription("Savings Transfer");
        transfer.setFromAccount(fromAccount);
        transfer.setToAccount(toAccount);
        transfer.setUserId(userId);

        Transfer saved = transferRepository.save(transfer);
        Optional<Transfer> found = transferRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getFromAmount()).isEqualByComparingTo("100.00");
        assertThat(found.get().getToAmount()).isEqualByComparingTo("100.00");
        assertThat(found.get().getDescription()).isEqualTo("Savings Transfer");
    }

    @Test
    void shouldFindTransfersByUserId() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        Account account1 = new Account();
        account1.setName("User1 From");
        account1.setType(AccountType.BANK);
        account1.setInitialBalance(BigDecimal.valueOf(1000.00));
        account1.setBalance(BigDecimal.valueOf(1000.00));
        account1.setUserId(userId1);
        accountRepository.save(account1);

        Account account2 = new Account();
        account2.setName("User1 To");
        account2.setType(AccountType.BANK);
        account2.setInitialBalance(BigDecimal.valueOf(500.00));
        account2.setBalance(BigDecimal.valueOf(500.00));
        account2.setUserId(userId1);
        accountRepository.save(account2);

        Transfer transfer = new Transfer();
        transfer.setFromAmount(BigDecimal.valueOf(100.00));
        transfer.setToAmount(BigDecimal.valueOf(100.00));
        transfer.setAdjustment(BigDecimal.ZERO);
        transfer.setTransactionDate(OffsetDateTime.now());
        transfer.setDescription("User1 Transfer");
        transfer.setFromAccount(account1);
        transfer.setToAccount(account2);
        transfer.setUserId(userId1);
        transferRepository.save(transfer);

        List<Transfer> user1Transfers = transferRepository.findAllByUserId(userId1);
        List<Transfer> user2Transfers = transferRepository.findAllByUserId(userId2);

        assertThat(user1Transfers).hasSize(1);
        assertThat(user1Transfers.getFirst().getDescription()).isEqualTo("User1 Transfer");
        assertThat(user2Transfers).isEmpty();
    }
}
