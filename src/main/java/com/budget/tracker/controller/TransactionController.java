package com.budget.tracker.controller;

import com.budget.tracker.model.Account;
import com.budget.tracker.model.Category;
import com.budget.tracker.model.Transaction;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.payload.request.TransferRequest;
import com.budget.tracker.service.AccountService;
import com.budget.tracker.service.CategoryService;
import com.budget.tracker.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final AccountService accountService;
    private final CategoryService categoryService;

    public TransactionController(TransactionService transactionService, AccountService accountService, CategoryService categoryService) {
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<Transaction> getAllTransactions() {
        return transactionService.getAllTransactionsForUser();
    }

    @GetMapping("/{id}")
    public Transaction getTransactionById(@PathVariable UUID id) {
        return transactionService.getTransactionById(id);
    }

    @PostMapping
    public Transaction createTransaction(@Valid @RequestBody Transaction transaction) {
        return transactionService.createTransaction(transaction);
    }

    @PostMapping("/transfer")
    public Transaction createTransfer(@Valid @RequestBody TransferRequest transferRequest) {
        Account fromAccount = accountService.getAccountById(transferRequest.getFromAccountId());
        Account toAccount = accountService.getAccountById(transferRequest.getToAccountId());
        
        Transaction sourceTransaction = new Transaction();
        sourceTransaction.setAccount(fromAccount);
        sourceTransaction.setAmount(transferRequest.getAmount());
        sourceTransaction.setDescription(transferRequest.getDescription());
        sourceTransaction.setTransactionDate(transferRequest.getTransactionDate());
        sourceTransaction.setType(TransactionType.TRANSFER);
        
        if (transferRequest.getCategoryId() != null) {
            Category category = categoryService.getCategoryById(transferRequest.getCategoryId());
            sourceTransaction.setCategory(category);
        }
        
        return transactionService.createTransfer(sourceTransaction, toAccount);
    }

    @PutMapping("/{id}")
    public Transaction updateTransaction(@PathVariable UUID id, @Valid @RequestBody Transaction transactionDetails) {
        return transactionService.updateTransaction(id, transactionDetails);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable UUID id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }
}
