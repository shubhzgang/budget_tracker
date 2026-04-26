package com.budget.tracker.service;

import com.budget.tracker.model.Account;
import com.budget.tracker.model.Transaction;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.repository.AccountRepository;
import com.budget.tracker.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransactionService(TransactionRepository transactionRepository, AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    public Transaction createTransaction(Transaction transaction) {
        TransactionType type = transaction.getType();
        if ((type == TransactionType.INCOME || type == TransactionType.EXPENSE || type == TransactionType.TRANSFER)
                && transaction.getAmount() != null) {
            updateBalance(transaction.getAccount().getId(), transaction.getUserId(), type, transaction.getAmount());
        }
        return transactionRepository.save(transaction);
    }

    public Transaction getTransactionById(UUID transactionId, UUID userId) {
        return transactionRepository.findAllByUserId(userId).stream()
                .filter(t -> t.getId().equals(transactionId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Transaction not found or access denied"));
    }

    public List<Transaction> getAllTransactionsForUser(UUID userId) {
        return transactionRepository.findAllByUserId(userId);
    }

    public List<Transaction> getTransactionsForAccount(UUID accountId, UUID userId) {
        return transactionRepository.findAllByAccountIdAndUserId(accountId, userId);
    }

    public Transaction updateTransaction(UUID transactionId, UUID userId, Transaction transactionDetails) {
        Transaction existing = getTransactionById(transactionId, userId);
        TransactionType type = existing.getType();

        if (type == TransactionType.TRANSFER || type == TransactionType.LEND || type == TransactionType.BORROW) {
            if (existing.getLinkedTransferId() != null) {
                Transaction linked = transactionRepository.findAllByUserId(userId).stream()
                        .filter(t -> existing.getLinkedTransferId().equals(t.getLinkedTransferId()))
                        .findFirst().orElse(null);

                BigDecimal amountDiff = transactionDetails.getAmount().subtract(existing.getAmount());
                if (amountDiff.compareTo(BigDecimal.ZERO) != 0) {
                    updateBalance(existing.getAccount().getId(), userId, type, amountDiff.negate());
                    if (linked != null) {
                        updateBalance(linked.getAccount().getId(), userId, linked.getType(), amountDiff.negate());
                    }
                }
            } else {
                if (existing.getAmount() != null) {
                    updateBalance(existing.getAccount().getId(), userId, type, existing.getAmount().negate());
                }
            }
        } else if (existing.getAmount() != null) {
            updateBalance(existing.getAccount().getId(), userId, type, existing.getAmount().negate());
        }

        existing.setAmount(transactionDetails.getAmount());
        existing.setDescription(transactionDetails.getDescription());
        existing.setTransactionDate(transactionDetails.getTransactionDate());
        existing.setCategory(transactionDetails.getCategory());
        existing.setLabel(transactionDetails.getLabel());
        return transactionRepository.save(existing);
    }

    @Transactional
    public void deleteTransaction(UUID transactionId, UUID userId) {
        Transaction transaction = getTransactionById(transactionId, userId);
        TransactionType type = transaction.getType();

        if (type == TransactionType.INCOME || type == TransactionType.EXPENSE) {
            if (transaction.getAmount() != null) {
                updateBalance(transaction.getAccount().getId(), userId, type, transaction.getAmount().negate());
            }
        } else if ((type == TransactionType.TRANSFER || type == TransactionType.LEND || type == TransactionType.BORROW)) {
            if (transaction.getLinkedTransferId() != null) {
                Transaction linked = transactionRepository.findAllByUserId(userId).stream()
                        .filter(t -> transaction.getLinkedTransferId().equals(t.getLinkedTransferId()))
                        .findFirst().orElse(null);

                if (transaction.getAmount() != null) {
                    updateBalance(transaction.getAccount().getId(), userId, type, transaction.getAmount().negate());
                    if (linked != null && linked.getAmount() != null) {
                        updateBalance(linked.getAccount().getId(), userId, linked.getType(), linked.getAmount().negate());
                    }
                }
            } else {
                if (transaction.getAmount() != null) {
                    updateBalance(transaction.getAccount().getId(), userId, type, transaction.getAmount().negate());
                }
            }
        }
        transactionRepository.delete(transaction);
    }

    @Transactional
    public Transaction createTransfer(Transaction sourceTransaction, Account toAccount) {
        if (sourceTransaction.getAmount() == null) {
            throw new IllegalArgumentException("Transfer amount must not be null");
        }

        TransactionType type = sourceTransaction.getType();
        if (type != TransactionType.TRANSFER && type != TransactionType.LEND && type != TransactionType.BORROW) {
            throw new IllegalArgumentException("Transaction type must be TRANSFER, LEND, or BORROW for transfer");
        }

        Account fromAccount = sourceTransaction.getAccount();
        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }

        if (fromAccount.getUserId() == null || !fromAccount.getUserId().equals(toAccount.getUserId())) {
            throw new IllegalArgumentException("Both accounts must belong to the same user");
        }

        // Validate dest account belongs to user
        List<Account> userAccounts = accountRepository.findAllByUserId(sourceTransaction.getUserId());
        boolean destExists = userAccounts.stream().anyMatch(a -> a.getId().equals(toAccount.getId()));
        if (!destExists) {
            throw new RuntimeException("Destination account not found or access denied");
        }

        // Create paired transaction
        Transaction destTransaction = new Transaction();
        destTransaction.setType(type);
        destTransaction.setAmount(sourceTransaction.getAmount());
        destTransaction.setDescription(sourceTransaction.getDescription());
        destTransaction.setTransactionDate(sourceTransaction.getTransactionDate());
        destTransaction.setAccount(toAccount);
        destTransaction.setUserId(sourceTransaction.getUserId());
        destTransaction.setCategory(sourceTransaction.getCategory());
        destTransaction.setLinkedTransferId(sourceTransaction.getId());

        Transaction savedSource = transactionRepository.save(sourceTransaction);
        savedSource.setLinkedTransferId(sourceTransaction.getId());
        Transaction savedDest = transactionRepository.save(destTransaction);
        savedSource.setLinkedTransferId(savedDest.getId());
        transactionRepository.save(savedSource);

        updateBalance(fromAccount.getId(), sourceTransaction.getUserId(), type, sourceTransaction.getAmount().negate());
        updateBalance(toAccount.getId(), sourceTransaction.getUserId(), type, sourceTransaction.getAmount());

        return savedSource;
    }

    private void updateBalance(UUID accountId, UUID userId, TransactionType type, BigDecimal amount) {
        Account account = accountRepository.findAllByUserId(userId).stream()
                .filter(a -> a.getId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Account not found or access denied"));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }
}
