package com.budget.tracker.service;

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

    public Account createAccount(Account account) {
        return accountRepository.save(account);
    }

    public Account getAccountById(UUID accountId, UUID userId) {
        return accountRepository.findAllByUserId(userId).stream()
                .filter(account -> account.getId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Account not found or access denied"));
    }

    public List<Account> getAllAccountsForUser(UUID userId) {
        return accountRepository.findAllByUserId(userId);
    }

    public Account updateAccount(UUID accountId, UUID userId, Account accountDetails) {
        Account account = getAccountById(accountId, userId);
        account.setName(accountDetails.getName());
        account.setType(accountDetails.getType());
        account.setBalance(accountDetails.getBalance());
        account.setCreditLimit(accountDetails.getCreditLimit());
        return accountRepository.save(account);
    }

    public void deleteAccount(UUID accountId, UUID userId) {
        Account account = getAccountById(accountId, userId);
        accountRepository.delete(account);
    }

    public BigDecimal calculateAvailableCredit(UUID accountId, UUID userId) {
        Account account = getAccountById(accountId, userId);
        if (account.getType() == AccountType.CREDIT_CARD && account.getCreditLimit() != null) {
            return account.getCreditLimit().subtract(account.getBalance());
        }
        return null;
    }
}