package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.Transaction;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.repository.AccountRepository;
import com.budget.tracker.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    private enum BalanceAction {
        APPLY, REVERT
    }

    public TransactionService(TransactionRepository transactionRepository, AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    private UUID getCurrentUserId() {
        UUID userId = AuthContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("No authenticated user found in context");
        }
        return userId;
    }

    @Transactional
    public Transaction createTransaction(Transaction transaction) {
        UUID userId = getCurrentUserId();
        transaction.setUserId(userId);
        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero");
        }

        // Fetch and set account to ensure it's managed and belongs to user
        Account account = accountRepository.findById(transaction.getAccount().getId())
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Account not found or access denied"));
        transaction.setAccount(account);

        updateBalance(account.getId(), userId, transaction.getType(), transaction.getAmount(), BalanceAction.APPLY);
        return transactionRepository.save(transaction);
    }

    public Transaction getTransactionById(UUID transactionId) {
        return transactionRepository.findAllByUserId(getCurrentUserId()).stream()
                .filter(t -> t.getId().equals(transactionId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Transaction not found or access denied"));
    }

    public List<Transaction> getAllTransactionsForUser() {
        return transactionRepository.findAllByUserId(getCurrentUserId());
    }

    public Page<Transaction> getTransactions(String searchTerm, TransactionType type, OffsetDateTime startDate, OffsetDateTime endDate, Pageable pageable) {
        return transactionRepository.searchTransactions(getCurrentUserId(), searchTerm, type, startDate, endDate, pageable);
    }

    public List<Transaction> getTransactionsForAccount(UUID accountId) {
        return transactionRepository.findAccountTransactions(accountId, getCurrentUserId());
    }

    @Transactional
    public Transaction updateTransaction(UUID transactionId, Transaction transactionDetails) {
        UUID userId = getCurrentUserId();
        Transaction existing = getTransactionById(transactionId);
        TransactionType oldType = existing.getType();
        BigDecimal oldAmount = existing.getAmount();

        updateBalance(existing.getAccount().getId(), userId, oldType, oldAmount, BalanceAction.REVERT);
        updateBalance(existing.getAccount().getId(), userId, transactionDetails.getType(), transactionDetails.getAmount(), BalanceAction.APPLY);

        existing.setAmount(transactionDetails.getAmount());
        existing.setType(transactionDetails.getType());
        existing.setDescription(transactionDetails.getDescription());
        existing.setTransactionDate(transactionDetails.getTransactionDate());
        existing.setCategory(transactionDetails.getCategory());
        existing.setLabels(transactionDetails.getLabels());

        return transactionRepository.save(existing);
    }

    @Transactional
    public void deleteTransaction(UUID transactionId) {
        UUID userId = getCurrentUserId();
        Transaction transaction = getTransactionById(transactionId);
        TransactionType type = transaction.getType();

        updateBalance(transaction.getAccount().getId(), userId, type, transaction.getAmount(), BalanceAction.REVERT);
        transactionRepository.delete(transaction);
    }

    private void updateBalance(UUID accountId, UUID userId, TransactionType type, BigDecimal amount, BalanceAction action) {
        Account account = accountRepository.findById(accountId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Account not found or access denied for userId: " + userId + " and accountId: " + accountId));

        boolean shouldAdd;
        if (type == TransactionType.INCOME || type == TransactionType.BORROW) {
            shouldAdd = true;
        } else {
            shouldAdd = false;
        }

        // CREDIT_CARD accounts represent debt, so logic is inverted
        if (account.getType() == com.budget.tracker.model.AccountType.CREDIT_CARD) {
            shouldAdd = !shouldAdd;
        }

        if (action == BalanceAction.REVERT) {
            shouldAdd = !shouldAdd;
        }

        if (shouldAdd) {
            account.setBalance(account.getBalance().add(amount));
        } else {
            account.setBalance(account.getBalance().subtract(amount));
        }
        accountRepository.save(account);
    }
}
