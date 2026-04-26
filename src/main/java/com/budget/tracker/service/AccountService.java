package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    private UUID getCurrentUserId() {
        UUID userId = AuthContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("No authenticated user found in context");
        }
        return userId;
    }

    public Account createAccount(Account account) {
        account.setUserId(getCurrentUserId());
        if (account.getInitialBalance() == null) {
            account.setInitialBalance(BigDecimal.ZERO);
        }
        if (account.getBalance() == null) {
            account.setBalance(account.getInitialBalance());
        }
        return accountRepository.save(account);
    }

    public Account getAccountById(UUID accountId) {
        return accountRepository.findAllByUserId(getCurrentUserId()).stream()
                .filter(account -> account.getId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Account not found or access denied"));
    }

    public List<Account> getAllAccountsForUser() {
        return accountRepository.findAllByUserId(getCurrentUserId());
    }

    public Account updateAccount(UUID accountId, Account accountDetails) {
        Account account = getAccountById(accountId);
        
        // Adjust balance if initialBalance changed
        BigDecimal oldInitial = account.getInitialBalance();
        BigDecimal newInitial = accountDetails.getInitialBalance();
        if (newInitial != null && oldInitial.compareTo(newInitial) != 0) {
            BigDecimal delta = newInitial.subtract(oldInitial);
            account.setBalance(account.getBalance().add(delta));
            account.setInitialBalance(newInitial);
        }

        account.setName(accountDetails.getName());
        account.setType(accountDetails.getType());
        account.setCreditLimit(accountDetails.getCreditLimit());
        return accountRepository.save(account);
    }

    public void deleteAccount(UUID accountId) {
        Account account = getAccountById(accountId);
        accountRepository.delete(account);
    }

    public BigDecimal calculateAvailableCredit(UUID accountId) {
        Account account = getAccountById(accountId);
        if (account.getType() == AccountType.CREDIT_CARD && account.getCreditLimit() != null) {
            return account.getCreditLimit().subtract(account.getBalance());
        }
        return null;
    }
}
