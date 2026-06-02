package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.Category;
import com.budget.tracker.model.Label;
import com.budget.tracker.model.Transfer;
import com.budget.tracker.payload.request.TransferRequest;
import com.budget.tracker.repository.AccountRepository;
import com.budget.tracker.repository.CategoryRepository;
import com.budget.tracker.repository.LabelRepository;
import com.budget.tracker.repository.TransferRepository;
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
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final LabelRepository labelRepository;

    public TransferService(TransferRepository transferRepository,
                           AccountRepository accountRepository,
                           CategoryRepository categoryRepository,
                           LabelRepository labelRepository) {
        this.transferRepository = transferRepository;
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
    public Transfer createTransfer(TransferRequest request) {
        UUID userId = getCurrentUserId();

        if (!request.isValid()) {
            throw new IllegalArgumentException("Exactly two of fromAmount, toAmount, adjustment must be provided");
        }

        BigDecimal fromAmount = request.getFromAmount();
        BigDecimal toAmount = request.getToAmount();
        BigDecimal adjustment = request.getAdjustment();

        if (fromAmount != null && toAmount != null) {
            adjustment = toAmount.subtract(fromAmount);
        } else if (fromAmount != null && adjustment != null) {
            toAmount = fromAmount.add(adjustment);
        } else if (toAmount != null && adjustment != null) {
            fromAmount = toAmount.subtract(adjustment);
        }

        if (fromAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("From amount must be greater than zero");
        }
        if (toAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("To amount must be greater than zero");
        }

        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }

        Account fromAccount = accountRepository.findById(request.getFromAccountId())
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Source account not found or access denied"));

        Account toAccount = accountRepository.findById(request.getToAccountId())
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Destination account not found or access denied"));

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

        Transfer transfer = new Transfer();
        transfer.setUserId(userId);
        transfer.setFromAccount(fromAccount);
        transfer.setToAccount(toAccount);
        transfer.setCategory(category);
        transfer.setLabels(labels);
        transfer.setFromAmount(fromAmount);
        transfer.setToAmount(toAmount);
        transfer.setAdjustment(adjustment);
        transfer.setDescription(request.getDescription());
        transfer.setTransactionDate(request.getTransactionDate());

        Transfer saved = transferRepository.save(transfer);

        // Debit source account fromAmount
        adjustAccountBalance(fromAccount.getId(), userId, fromAmount, false, false);
        // Credit destination account toAmount
        adjustAccountBalance(toAccount.getId(), userId, toAmount, true, false);

        return saved;
    }

    public Transfer getTransferById(UUID id) {
        UUID userId = getCurrentUserId();
        return transferRepository.findById(id)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Transfer not found or access denied"));
    }

    public Page<Transfer> getAllTransfers(String searchTerm, OffsetDateTime startDate, OffsetDateTime endDate, Pageable pageable) {
        return transferRepository.searchTransfers(getCurrentUserId(), searchTerm, startDate, endDate, pageable);
    }

    public List<Transfer> getTransfersForAccount(UUID accountId) {
        return transferRepository.findAccountTransfers(accountId, getCurrentUserId());
    }

    @Transactional
    public Transfer updateTransfer(UUID id, TransferRequest request) {
        UUID userId = getCurrentUserId();
        Transfer existing = getTransferById(id);

        // Revert old balances
        adjustAccountBalance(existing.getFromAccount().getId(), userId, existing.getFromAmount(), false, true);
        adjustAccountBalance(existing.getToAccount().getId(), userId, existing.getToAmount(), true, true);

        if (!request.isValid()) {
            throw new IllegalArgumentException("Exactly two of fromAmount, toAmount, adjustment must be provided");
        }

        BigDecimal fromAmount = request.getFromAmount();
        BigDecimal toAmount = request.getToAmount();
        BigDecimal adjustment = request.getAdjustment();

        if (fromAmount != null && toAmount != null) {
            adjustment = toAmount.subtract(fromAmount);
        } else if (fromAmount != null && adjustment != null) {
            toAmount = fromAmount.add(adjustment);
        } else if (toAmount != null && adjustment != null) {
            fromAmount = toAmount.subtract(adjustment);
        }

        if (fromAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("From amount must be greater than zero");
        }
        if (toAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("To amount must be greater than zero");
        }

        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }

        Account fromAccount = accountRepository.findById(request.getFromAccountId())
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Source account not found or access denied"));

        Account toAccount = accountRepository.findById(request.getToAccountId())
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Destination account not found or access denied"));

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

        existing.setFromAccount(fromAccount);
        existing.setToAccount(toAccount);
        existing.setCategory(category);
        existing.setLabels(labels);
        existing.setFromAmount(fromAmount);
        existing.setToAmount(toAmount);
        existing.setAdjustment(adjustment);
        existing.setDescription(request.getDescription());
        existing.setTransactionDate(request.getTransactionDate());

        Transfer saved = transferRepository.save(existing);

        // Apply new balances
        adjustAccountBalance(fromAccount.getId(), userId, fromAmount, false, false);
        adjustAccountBalance(toAccount.getId(), userId, toAmount, true, false);

        return saved;
    }

    @Transactional
    public void deleteTransfer(UUID id) {
        UUID userId = getCurrentUserId();
        Transfer existing = getTransferById(id);

        // Revert balances
        adjustAccountBalance(existing.getFromAccount().getId(), userId, existing.getFromAmount(), false, true);
        adjustAccountBalance(existing.getToAccount().getId(), userId, existing.getToAmount(), true, true);

        transferRepository.delete(existing);
    }

    private void adjustAccountBalance(UUID accountId, UUID userId, BigDecimal amount, boolean isDestination, boolean isRevert) {
        Account account = accountRepository.findById(accountId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Account not found or access denied"));

        boolean shouldAdd = isDestination;

        if (account.getType() == com.budget.tracker.model.AccountType.CREDIT_CARD) {
            shouldAdd = !shouldAdd;
        }

        if (isRevert) {
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
