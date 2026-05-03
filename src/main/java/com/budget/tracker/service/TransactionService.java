package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.Transaction;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.repository.AccountRepository;
import com.budget.tracker.repository.TransactionRepository;
import com.github.f4b6a3.uuid.UuidCreator;
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
        if (transaction.getAmount() == null) {
            throw new IllegalArgumentException("Transaction amount must not be null");
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
        return transactionRepository.findAllByAccountIdAndUserId(accountId, getCurrentUserId());
    }

    @Transactional
    public Transaction updateTransaction(UUID transactionId, Transaction transactionDetails) {
        UUID userId = getCurrentUserId();
        Transaction existing = getTransactionById(transactionId);
        TransactionType oldType = existing.getType();
        BigDecimal oldAmount = existing.getAmount();

        if (oldType == TransactionType.TRANSFER) {
            if (existing.getLinkedTransferId() != null) {
                Transaction linked = transactionRepository.findById(existing.getLinkedTransferId())
                        .orElseThrow(() -> new RuntimeException("Linked transaction not found"));

                // Revert old transfer
                updateBalance(existing.getAccount().getId(), userId, oldType, oldAmount, BalanceAction.REVERT, false);
                updateBalance(linked.getAccount().getId(), userId, linked.getType(), linked.getAmount(), BalanceAction.REVERT, true);

                // Apply new transfer
                BigDecimal newAmount = transactionDetails.getAmount();
                updateBalance(existing.getAccount().getId(), userId, oldType, newAmount, BalanceAction.APPLY, false);
                updateBalance(linked.getAccount().getId(), userId, linked.getType(), newAmount, BalanceAction.APPLY, true);

                linked.setAmount(newAmount);
                linked.setDescription(transactionDetails.getDescription());
                linked.setTransactionDate(transactionDetails.getTransactionDate());
                transactionRepository.save(linked);
            } else {
                updateBalance(existing.getAccount().getId(), userId, oldType, oldAmount, BalanceAction.REVERT, false);
                updateBalance(existing.getAccount().getId(), userId, oldType, transactionDetails.getAmount(), BalanceAction.APPLY, false);
            }
        } else {
            // Revert old
            updateBalance(existing.getAccount().getId(), userId, oldType, oldAmount, BalanceAction.REVERT, false);
            // Apply new
            updateBalance(existing.getAccount().getId(), userId, oldType, transactionDetails.getAmount(), BalanceAction.APPLY, false);
        }

        existing.setAmount(transactionDetails.getAmount());
        existing.setDescription(transactionDetails.getDescription());
        existing.setTransactionDate(transactionDetails.getTransactionDate());
        existing.setCategory(transactionDetails.getCategory());
        existing.setLabel(transactionDetails.getLabel());
        return transactionRepository.save(existing);
    }

    @Transactional
    public void deleteTransaction(UUID transactionId) {
        UUID userId = getCurrentUserId();
        Transaction transaction = getTransactionById(transactionId);
        TransactionType type = transaction.getType();

        if (type == TransactionType.TRANSFER) {
            if (transaction.getLinkedTransferId() != null) {
                Transaction linked = transactionRepository.findById(transaction.getLinkedTransferId()).orElse(null);
                updateBalance(transaction.getAccount().getId(), userId, type, transaction.getAmount(), BalanceAction.REVERT, false);
                if (linked != null) {
                    updateBalance(linked.getAccount().getId(), userId, linked.getType(), linked.getAmount(), BalanceAction.REVERT, true);
                    transactionRepository.delete(linked);
                }
            } else {
                updateBalance(transaction.getAccount().getId(), userId, type, transaction.getAmount(), BalanceAction.REVERT, false);
            }
        } else {
            updateBalance(transaction.getAccount().getId(), userId, type, transaction.getAmount(), BalanceAction.REVERT, false);
        }
        transactionRepository.delete(transaction);
    }

    @Transactional
    public Transaction createTransfer(Transaction sourceTransaction, Account toAccount) {
        UUID userId = getCurrentUserId();
        sourceTransaction.setUserId(userId);
        if (sourceTransaction.getAmount() == null) {
            throw new IllegalArgumentException("Transfer amount must not be null");
        }

        TransactionType type = sourceTransaction.getType();
        if (type != TransactionType.TRANSFER) {
            throw new IllegalArgumentException("Transaction type must be TRANSFER for transfer");
        }

        Account fromAccount = sourceTransaction.getAccount();
        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }

        if (fromAccount.getUserId() == null || !fromAccount.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Source account must belong to the user");
        }

        // Validate dest account belongs to user
        accountRepository.findById(toAccount.getId())
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Destination account not found or access denied"));

        // Create paired transaction
        if (sourceTransaction.getId() == null) {
            sourceTransaction.setId(UuidCreator.getTimeOrderedEpoch());
        }

        Transaction destTransaction = new Transaction();
        destTransaction.setId(UuidCreator.getTimeOrderedEpoch());
        destTransaction.setType(type);
        destTransaction.setAmount(sourceTransaction.getAmount());
        destTransaction.setDescription(sourceTransaction.getDescription());
        destTransaction.setTransactionDate(sourceTransaction.getTransactionDate());
        destTransaction.setAccount(toAccount);
        destTransaction.setUserId(userId);
        destTransaction.setCategory(sourceTransaction.getCategory());

        // Set transfer metadata
        sourceTransaction.setLinkedAccount(toAccount);
        sourceTransaction.setIsIncomingTransfer(false);
        destTransaction.setLinkedAccount(fromAccount);
        destTransaction.setIsIncomingTransfer(true);

        // First save without linked IDs to satisfy immediate foreign key constraint
        transactionRepository.save(sourceTransaction);
        transactionRepository.save(destTransaction);

        // Now link them
        sourceTransaction.setLinkedTransferId(destTransaction.getId());
        destTransaction.setLinkedTransferId(sourceTransaction.getId());

        Transaction savedSource = transactionRepository.save(sourceTransaction);
        transactionRepository.save(destTransaction);

        updateBalance(fromAccount.getId(), userId, type, sourceTransaction.getAmount(), BalanceAction.APPLY, false);
        updateBalance(toAccount.getId(), userId, type, sourceTransaction.getAmount(), BalanceAction.APPLY, true);

        return savedSource;
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
