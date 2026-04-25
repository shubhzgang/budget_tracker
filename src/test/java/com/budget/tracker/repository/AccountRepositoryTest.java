package com.budget.tracker.repository;

import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void shouldSaveAndFindAccount() {
        Account account = new Account();
        account.setName("Test Account");
        account.setType(AccountType.CASH);
        account.setBalance(BigDecimal.valueOf(100.00));
        account.setUserId(UUID.randomUUID());

        Account savedAccount = accountRepository.save(account);
        java.util.Optional<Account> foundAccount = accountRepository.findById(savedAccount.getId());

        assertThat(foundAccount).isPresent();
        assertThat(foundAccount.get().getName()).isEqualTo("Test Account");
        assertThat(foundAccount.get().getType()).isEqualTo(AccountType.CASH);
        assertThat(foundAccount.get().getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldFindAccountsByUserId() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        Account account1 = new Account();
        account1.setName("User1 Account");
        account1.setType(AccountType.CASH);
        account1.setBalance(BigDecimal.valueOf(100.00));
        account1.setUserId(userId1);

        Account account2 = new Account();
        account2.setName("User2 Account");
        account2.setType(AccountType.CASH);
        account2.setBalance(BigDecimal.valueOf(200.00));
        account2.setUserId(userId2);

        accountRepository.save(account1);
        accountRepository.save(account2);

        List<Account> user1Accounts = accountRepository.findAllByUserId(userId1);
        List<Account> user2Accounts = accountRepository.findAllByUserId(userId2);

        assertThat(user1Accounts).hasSize(1);
        assertThat(user1Accounts.getFirst().getName()).isEqualTo("User1 Account");
        assertThat(user2Accounts).hasSize(1);
        assertThat(user2Accounts.getFirst().getName()).isEqualTo("User2 Account");
    }

    @Test
    void shouldNotFindAccountByUserIdIfOwnedByAnotherUser() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        Account account1 = new Account();
        account1.setName("User1 Account");
        account1.setType(AccountType.CASH);
        account1.setBalance(BigDecimal.valueOf(100.00));
        account1.setUserId(userId1);
        accountRepository.save(account1);

        List<Account> user2Accounts = accountRepository.findAllByUserId(userId2);

        assertThat(user2Accounts).isEmpty();
    }
}
