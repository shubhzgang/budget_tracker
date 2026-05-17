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
        TransactionType type = transaction.getType();
        if (type == TransactionType.TRANSFER) {
            throw new IllegalArgumentException("Use createTransfer for TRANSFER type");
        }

        // Fetch and set account to ensure it's managed and belongs to user
        Account account = accountRepository.findById(transaction.getAccount().getId())
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Account not found or access denied"));
        transaction.setAccount(account);

        updateBalance(account.getId(), userId, type, transaction.getAmount(), BalanceAction.APPLY, false);
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

       Account toAccountForApply = transactionDetails.getToAccount() != null ? transactionDetails.getToAccount() : existing.getToAccount();
        if (oldType == TransactionType.TRANSFER) {
            updateBalance(existing.getAccount().getId(), userId, oldType, oldAmount, BalanceAction.REVERT, false);
            if (existing.getToAccount() != null) {
                updateBalance(existing.getToAccount().getId(), userId, oldType, oldAmount, BalanceAction.REVERT, true);
            }
            updateBalance(existing.getAccount().getId(), userId, oldType, transactionDetails.getAmount(), BalanceAction.APPLY, false);
            if (toAccountForApply != null) {
                updateBalance(toAccountForApply.getId(), userId, oldType, transactionDetails.getAmount(), BalanceAction.APPLY, true);
            }
        } else {
            updateBalance(existing.getAccount().getId(), userId, oldType, oldAmount, BalanceAction.REVERT, false);
            updateBalance(existing.getAccount().getId(), userId, oldType, transactionDetails.getAmount(), BalanceAction.APPLY, false);
        }

        existing.setAmount(transactionDetails.getAmount());
        existing.setDescription(transactionDetails.getDescription());
        existing.setTransactionDate(transactionDetails.getTransactionDate());
        existing.setCategory(transactionDetails.getCategory());
        existing.setLabel(transactionDetails.getLabel());
        if (transactionDetails.getToAccount() != null) {
            existing.setToAccount(transactionDetails.getToAccount());
        }
        return transactionRepository.save(existing);
    }

    @Transactional
    public void deleteTransaction(UUID transactionId) {
        UUID userId = getCurrentUserId();
        Transaction transaction = getTransactionById(transactionId);
        TransactionType type = transaction.getType();

        if (type == TransactionType.TRANSFER) {
            updateBalance(transaction.getAccount().getId(), userId, type, transaction.getAmount(), BalanceAction.REVERT, false);
            if (transaction.getToAccount() != null) {
                updateBalance(transaction.getToAccount().getId(), userId, type, transaction.getAmount(), BalanceAction.REVERT, true);
            }
        } else {
            updateBalance(transaction.getAccount().getId(), userId, type, transaction.getAmount(), BalanceAction.REVERT, false);
        }
        transactionRepository.delete(transaction);
    }

    @Transactional
    public Transaction createTransfer(Transaction transaction, Account toAccount) {
        UUID userId = getCurrentUserId();
        transaction.setUserId(userId);
        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }

        TransactionType type = transaction.getType();
        if (type != TransactionType.TRANSFER) {
            throw new IllegalArgumentException("Transaction type must be TRANSFER for transfer");
        }

        Account fromAccount = transaction.getAccount();
        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }

        if (fromAccount.getUserId() == null || !fromAccount.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Source account must belong to the user");
        }

        accountRepository.findById(toAccount.getId())
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Destination account not found or access denied"));

        transaction.setToAccount(toAccount);
        transactionRepository.save(transaction);

        updateBalance(fromAccount.getId(), userId, type, transaction.getAmount(), BalanceAction.APPLY, false);
        updateBalance(toAccount.getId(), userId, type, transaction.getAmount(), BalanceAction.APPLY, true);

        return transaction;
    }

    private void updateBalance(UUID accountId, UUID userId, TransactionType type, BigDecimal amount, BalanceAction action, boolean isDestinationAccount) {
        Account account = accountRepository.findById(accountId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Account not found or access denied for userId: " + userId + " and accountId: " + accountId));

        boolean shouldAdd;
        if (type == TransactionType.INCOME || type == TransactionType.BORROW) {
            shouldAdd = true;
        } else if (type == TransactionType.EXPENSE || type == TransactionType.LEND) {
            shouldAdd = false;
        } else {
            // TRANSFER
            // Source of TRANSFER (My Bank) subtracts cash.
            // Dest of TRANSFER (Savings/Friend) adds balance.
            shouldAdd = isDestinationAccount;
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
