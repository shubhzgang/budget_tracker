package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.model.Category;
import com.budget.tracker.model.Label;
import com.budget.tracker.model.Transfer;
import com.budget.tracker.payload.request.TransferRequest;
import com.budget.tracker.repository.AccountRepository;
import com.budget.tracker.repository.CategoryRepository;
import com.budget.tracker.repository.LabelRepository;
import com.budget.tracker.repository.TransferRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private LabelRepository labelRepository;

    private TransferService transferService;

    private UUID userId;
    private UUID fromAccountId;
    private UUID toAccountId;

    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        transferService = new TransferService(transferRepository, accountRepository, categoryRepository, labelRepository);
        userId = UUID.randomUUID();
        fromAccountId = UUID.randomUUID();
        toAccountId = UUID.randomUUID();
        AuthContext.setUserId(userId);

        fromAccount = new Account();
        fromAccount.setId(fromAccountId);
        fromAccount.setUserId(userId);
        fromAccount.setBalance(new BigDecimal("1000.00"));
        fromAccount.setType(AccountType.BANK);

        toAccount = new Account();
        toAccount.setId(toAccountId);
        toAccount.setUserId(userId);
        toAccount.setBalance(new BigDecimal("500.00"));
        toAccount.setType(AccountType.BANK);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void createTransfer_fromAmount_adjustment_shouldComputeToAmountAndAdjustBalances() {
        TransferRequest req = new TransferRequest();
        req.setFromAccountId(fromAccountId);
        req.setToAccountId(toAccountId);
        req.setFromAmount(new BigDecimal("100.00"));
        req.setAdjustment(new BigDecimal("10.00"));
        req.setTransactionDate(OffsetDateTime.now());
        req.setDescription("Savings transfer");

        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(i -> i.getArgument(0));

        Transfer result = transferService.createTransfer(req);

        assertNotNull(result);
        assertEquals(new BigDecimal("110.00"), result.getToAmount());
        assertEquals(new BigDecimal("10.00"), result.getAdjustment());
        verify(transferRepository).save(any(Transfer.class));
        verify(accountRepository).save(argThat(a -> a.getId().equals(fromAccountId) && a.getBalance().compareTo(new BigDecimal("900.00")) == 0));
        verify(accountRepository).save(argThat(a -> a.getId().equals(toAccountId) && a.getBalance().compareTo(new BigDecimal("610.00")) == 0));
    }

    @Test
    void createTransfer_fromAmount_toAmount_shouldComputeAdjustmentAndAdjustBalances() {
        TransferRequest req = new TransferRequest();
        req.setFromAccountId(fromAccountId);
        req.setToAccountId(toAccountId);
        req.setFromAmount(new BigDecimal("95.00"));
        req.setToAmount(new BigDecimal("100.00"));
        req.setTransactionDate(OffsetDateTime.now());
        req.setDescription("CC Payment");

        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(i -> i.getArgument(0));

        Transfer result = transferService.createTransfer(req);

        assertNotNull(result);
        assertEquals(new BigDecimal("5.00"), result.getAdjustment());
        verify(accountRepository).save(argThat(a -> a.getId().equals(fromAccountId) && a.getBalance().compareTo(new BigDecimal("905.00")) == 0));
        verify(accountRepository).save(argThat(a -> a.getId().equals(toAccountId) && a.getBalance().compareTo(new BigDecimal("600.00")) == 0));
    }

    @Test
    void createTransfer_toAmount_adjustment_shouldComputeFromAmountAndAdjustBalances() {
        TransferRequest req = new TransferRequest();
        req.setFromAccountId(fromAccountId);
        req.setToAccountId(toAccountId);
        req.setToAmount(new BigDecimal("100.00"));
        req.setAdjustment(new BigDecimal("5.00"));
        req.setTransactionDate(OffsetDateTime.now());
        req.setDescription("CC Payment 2");

        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(i -> i.getArgument(0));

        Transfer result = transferService.createTransfer(req);

        assertNotNull(result);
        assertEquals(new BigDecimal("95.00"), result.getFromAmount());
        verify(accountRepository).save(argThat(a -> a.getId().equals(fromAccountId) && a.getBalance().compareTo(new BigDecimal("905.00")) == 0));
        verify(accountRepository).save(argThat(a -> a.getId().equals(toAccountId) && a.getBalance().compareTo(new BigDecimal("600.00")) == 0));
    }

    @Test
    void createTransfer_allThreeFields_shouldReject() {
        TransferRequest req = new TransferRequest();
        req.setFromAccountId(fromAccountId);
        req.setToAccountId(toAccountId);
        req.setFromAmount(new BigDecimal("100.00"));
        req.setToAmount(new BigDecimal("100.00"));
        req.setAdjustment(new BigDecimal("0.00"));

        assertThrows(IllegalArgumentException.class, () -> transferService.createTransfer(req));
    }

    @Test
    void createTransfer_zeroFromAmount_shouldThrow() {
        TransferRequest req = new TransferRequest();
        req.setFromAccountId(fromAccountId);
        req.setToAccountId(toAccountId);
        req.setFromAmount(BigDecimal.ZERO);
        req.setAdjustment(new BigDecimal("5.00"));

        assertThrows(IllegalArgumentException.class, () -> transferService.createTransfer(req));
    }

    @Test
    void createTransfer_sameAccount_shouldThrow() {
        TransferRequest req = new TransferRequest();
        req.setFromAccountId(fromAccountId);
        req.setToAccountId(fromAccountId);
        req.setFromAmount(new BigDecimal("10.00"));
        req.setAdjustment(new BigDecimal("0.00"));

        assertThrows(IllegalArgumentException.class, () -> transferService.createTransfer(req));
    }

    @Test
    void createTransfer_creditCardDestination_debtDecreases() {
        toAccount.setType(AccountType.CREDIT_CARD);
        toAccount.setBalance(new BigDecimal("500.00")); // represents ₹500 outstanding debt

        TransferRequest req = new TransferRequest();
        req.setFromAccountId(fromAccountId);
        req.setToAccountId(toAccountId);
        req.setFromAmount(new BigDecimal("95.00"));
        req.setAdjustment(new BigDecimal("5.00"));
        req.setTransactionDate(OffsetDateTime.now());
        req.setDescription("Pay CC bill");

        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(i -> i.getArgument(0));

        Transfer result = transferService.createTransfer(req);

        assertNotNull(result);
        assertEquals(new BigDecimal("100.00"), result.getToAmount());
        // Destination CC debt should DECREASE by toAmount (100): 500 - 100 = 400
        verify(accountRepository).save(argThat(a -> a.getId().equals(toAccountId) && a.getBalance().compareTo(new BigDecimal("400.00")) == 0));
        // Source BANK should decrease by fromAmount (95): 1000 - 95 = 905
        verify(accountRepository).save(argThat(a -> a.getId().equals(fromAccountId) && a.getBalance().compareTo(new BigDecimal("905.00")) == 0));
    }

    @Test
    void updateTransfer_shouldRevertAndApplyBalances() {
        Transfer existing = new Transfer();
        existing.setId(UUID.randomUUID());
        existing.setFromAccount(fromAccount);
        existing.setToAccount(toAccount);
        existing.setFromAmount(new BigDecimal("100.00"));
        existing.setToAmount(new BigDecimal("100.00"));
        existing.setAdjustment(BigDecimal.ZERO);
        existing.setUserId(userId);

        TransferRequest req = new TransferRequest();
        req.setFromAccountId(fromAccountId);
        req.setToAccountId(toAccountId);
        req.setFromAmount(new BigDecimal("200.00"));
        req.setToAmount(new BigDecimal("200.00"));
        req.setTransactionDate(OffsetDateTime.now());
        req.setDescription("Updated");

        when(transferRepository.findById(any())).thenReturn(Optional.of(existing));
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(i -> i.getArgument(0));

        Transfer updated = transferService.updateTransfer(existing.getId(), req);

        assertNotNull(updated);
        // Revert bank: 1000 + 100 (fromAmount) -> 1100. Apply new: 1100 - 200 -> 900.
        verify(accountRepository, atLeastOnce()).save(argThat(a -> a.getId().equals(fromAccountId) && a.getBalance().compareTo(new BigDecimal("900.00")) == 0));
        // Revert dest: 500 - 100 (toAmount) -> 400. Apply new: 400 + 200 -> 600.
        verify(accountRepository, atLeastOnce()).save(argThat(a -> a.getId().equals(toAccountId) && a.getBalance().compareTo(new BigDecimal("600.00")) == 0));
    }

    @Test
    void deleteTransfer_shouldReverseBalances() {
        Transfer existing = new Transfer();
        existing.setId(UUID.randomUUID());
        existing.setFromAccount(fromAccount);
        existing.setToAccount(toAccount);
        existing.setFromAmount(new BigDecimal("100.00"));
        existing.setToAmount(new BigDecimal("100.00"));
        existing.setAdjustment(BigDecimal.ZERO);
        existing.setUserId(userId);

        when(transferRepository.findById(any())).thenReturn(Optional.of(existing));
        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));

        transferService.deleteTransfer(existing.getId());

        // Revert bank: 1000 + 100 = 1100
        verify(accountRepository).save(argThat(a -> a.getId().equals(fromAccountId) && a.getBalance().compareTo(new BigDecimal("1100.00")) == 0));
        // Revert dest: 500 - 100 = 400
        verify(accountRepository).save(argThat(a -> a.getId().equals(toAccountId) && a.getBalance().compareTo(new BigDecimal("400.00")) == 0));
        verify(transferRepository).delete(existing);
    }

    @Test
    void createTransfer_unauthorizedFromAccount_shouldThrow() {
        fromAccount.setUserId(UUID.randomUUID()); // different user
        TransferRequest req = new TransferRequest();
        req.setFromAccountId(fromAccountId);
        req.setToAccountId(toAccountId);
        req.setFromAmount(new BigDecimal("100.00"));
        req.setAdjustment(new BigDecimal("10.00"));

        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));

        assertThrows(RuntimeException.class, () -> transferService.createTransfer(req));
    }

    @Test
    void createTransfer_unauthorizedToAccount_shouldThrow() {
        toAccount.setUserId(UUID.randomUUID()); // different user
        TransferRequest req = new TransferRequest();
        req.setFromAccountId(fromAccountId);
        req.setToAccountId(toAccountId);
        req.setFromAmount(new BigDecimal("100.00"));
        req.setAdjustment(new BigDecimal("10.00"));

        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));

        assertThrows(RuntimeException.class, () -> transferService.createTransfer(req));
    }

    @Test
    void createTransfer_unauthorizedCategory_shouldThrow() {
        Category cat = new Category();
        cat.setId(UUID.randomUUID());
        cat.setUserId(UUID.randomUUID()); // different user

        TransferRequest req = new TransferRequest();
        req.setFromAccountId(fromAccountId);
        req.setToAccountId(toAccountId);
        req.setFromAmount(new BigDecimal("100.00"));
        req.setAdjustment(new BigDecimal("10.00"));
        req.setCategoryId(cat.getId());

        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        when(categoryRepository.findById(cat.getId())).thenReturn(Optional.of(cat));

        assertThrows(RuntimeException.class, () -> transferService.createTransfer(req));
    }

    @Test
    void createTransfer_unauthorizedLabel_shouldThrow() {
        Label lbl = new Label();
        lbl.setId(UUID.randomUUID());
        lbl.setUserId(UUID.randomUUID()); // different user

        TransferRequest req = new TransferRequest();
        req.setFromAccountId(fromAccountId);
        req.setToAccountId(toAccountId);
        req.setFromAmount(new BigDecimal("100.00"));
        req.setAdjustment(new BigDecimal("10.00"));
        req.setLabelId(lbl.getId());

        when(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount));
        when(labelRepository.findById(lbl.getId())).thenReturn(Optional.of(lbl));

        assertThrows(RuntimeException.class, () -> transferService.createTransfer(req));
    }

    @Test
    void getTransferById_unauthorizedTransfer_shouldThrow() {
        Transfer t = new Transfer();
        t.setId(UUID.randomUUID());
        t.setUserId(UUID.randomUUID()); // different user

        when(transferRepository.findById(t.getId())).thenReturn(Optional.of(t));

        assertThrows(RuntimeException.class, () -> transferService.getTransferById(t.getId()));
    }

    @Test
    void updateTransfer_unauthorizedTransfer_shouldThrow() {
        Transfer t = new Transfer();
        t.setId(UUID.randomUUID());
        t.setUserId(UUID.randomUUID()); // different user

        TransferRequest req = new TransferRequest();
        req.setFromAccountId(fromAccountId);
        req.setToAccountId(toAccountId);
        req.setFromAmount(new BigDecimal("100.00"));
        req.setAdjustment(new BigDecimal("10.00"));

        when(transferRepository.findById(t.getId())).thenReturn(Optional.of(t));

        assertThrows(RuntimeException.class, () -> transferService.updateTransfer(t.getId(), req));
    }

    @Test
    void deleteTransfer_unauthorizedTransfer_shouldThrow() {
        Transfer t = new Transfer();
        t.setId(UUID.randomUUID());
        t.setUserId(UUID.randomUUID()); // different user

        when(transferRepository.findById(t.getId())).thenReturn(Optional.of(t));

        assertThrows(RuntimeException.class, () -> transferService.deleteTransfer(t.getId()));
    }
}
