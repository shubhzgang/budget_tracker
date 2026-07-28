package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.Category;
import com.budget.tracker.model.Label;
import com.budget.tracker.model.Transaction;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.payload.request.TransactionRequest;
import com.budget.tracker.repository.AccountRepository;
import com.budget.tracker.repository.CategoryRepository;
import com.budget.tracker.repository.LabelRepository;
import com.budget.tracker.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final LabelRepository labelRepository;

    private enum BalanceAction {
        APPLY, REVERT
    }

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              CategoryRepository categoryRepository,
                              LabelRepository labelRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.labelRepository = labelRepository;
    }

    private UUID getCurrentUserId() {
        UUID userId = AuthContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("No authenticated user found in context");
        }
        return userId;
    }

    @Transactional
    public Transaction createTransaction(TransactionRequest request) {
        UUID userId = getCurrentUserId();
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero");
        }

        Account account = accountRepository.findById(request.getAccountId())
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Account not found or access denied"));

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .filter(c -> c.getUserId().equals(userId))
                    .orElseThrow(() -> new RuntimeException("Category not found or access denied"));
        }

        Set<Label> labels = new HashSet<>();
        if (request.getLabelIds() != null && !request.getLabelIds().isEmpty()) {
            List<Label> fetchedLabels = labelRepository.findAllById(request.getLabelIds());
            for (Label label : fetchedLabels) {
                if (!label.getUserId().equals(userId)) {
                    throw new RuntimeException("Label not found or access denied");
                }
                labels.add(label);
            }
            if (labels.size() != request.getLabelIds().size()) {
                throw new RuntimeException("One or more labels not found");
            }
        }

        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAccount(account);
        transaction.setAmount(request.getAmount());
        transaction.setType(request.getType());
        transaction.setTransactionDate(request.getTransactionDate());
        transaction.setDescription(request.getDescription());
        transaction.setCategory(category);
        transaction.setLabels(labels);

        updateBalance(account.getId(), userId, transaction.getType(), transaction.getAmount(), BalanceAction.APPLY);
        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction createTransaction(Transaction transaction) {
        UUID userId = getCurrentUserId();
        transaction.setUserId(userId);
        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero");
        }

        Account account = accountRepository.findById(transaction.getAccount().getId())
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Account not found or access denied"));
        transaction.setAccount(account);

        updateBalance(account.getId(), userId, transaction.getType(), transaction.getAmount(), BalanceAction.APPLY);
        return transactionRepository.save(transaction);
    }

    public Transaction getTransactionById(UUID transactionId) {
        return transactionRepository.findByIdAndUserId(transactionId, getCurrentUserId())
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
    public Transaction updateTransaction(UUID transactionId, TransactionRequest request) {
        UUID userId = getCurrentUserId();
        Transaction existing = getTransactionById(transactionId);

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero");
        }

        // Revert balance on existing account with old type and amount
        updateBalance(existing.getAccount().getId(), userId, existing.getType(), existing.getAmount(), BalanceAction.REVERT);

        // Fetch new account (or verify existing)
        Account newAccount = accountRepository.findById(request.getAccountId())
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Account not found or access denied"));

        // Apply balance on new account with new type and amount
        updateBalance(newAccount.getId(), userId, request.getType(), request.getAmount(), BalanceAction.APPLY);

        existing.setAccount(newAccount);
        existing.setAmount(request.getAmount());
        existing.setType(request.getType());
        existing.setDescription(request.getDescription());
        existing.setTransactionDate(request.getTransactionDate());

        // Category: null means preserve existing category
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .filter(c -> c.getUserId().equals(userId))
                    .orElseThrow(() -> new RuntimeException("Category not found or access denied"));
            existing.setCategory(category);
        }

        // Labels: null means preserve existing labels, empty list means clear
        if (request.getLabelIds() != null) {
            if (request.getLabelIds().isEmpty()) {
                existing.setLabels(new HashSet<>());
            } else {
                List<Label> fetchedLabels = labelRepository.findAllById(request.getLabelIds());
                Set<Label> labels = new HashSet<>();
                for (Label label : fetchedLabels) {
                    if (!label.getUserId().equals(userId)) {
                        throw new RuntimeException("Label not found or access denied");
                    }
                    labels.add(label);
                }
                if (labels.size() != request.getLabelIds().size()) {
                    throw new RuntimeException("One or more labels not found");
                }
                existing.setLabels(labels);
            }
        }

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
