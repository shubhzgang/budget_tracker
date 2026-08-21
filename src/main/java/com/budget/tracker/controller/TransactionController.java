package com.budget.tracker.controller;

import com.budget.tracker.model.Transaction;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.payload.request.TransactionRequest;
import com.budget.tracker.payload.response.ExpenditureSummaryResponse;
import com.budget.tracker.service.ExpenditureSummaryService;
import com.budget.tracker.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final ExpenditureSummaryService expenditureSummaryService;

    public TransactionController(TransactionService transactionService,
                                 ExpenditureSummaryService expenditureSummaryService) {
        this.transactionService = transactionService;
        this.expenditureSummaryService = expenditureSummaryService;
    }

    @GetMapping("/expenditure-summary")
    public ExpenditureSummaryResponse getExpenditureSummary() {
        return expenditureSummaryService.getSummary();
    }

    @GetMapping
    public Page<Transaction> getAllTransactions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionService.getTransactions(search, type, startDate, endDate, pageable);
    }

    @GetMapping("/{id}")
    public Transaction getTransactionById(@PathVariable UUID id) {
        return transactionService.getTransactionById(id);
    }

    @PostMapping
    public Transaction createTransaction(@Valid @RequestBody TransactionRequest request) {
        return transactionService.createTransaction(request);
    }

    @PutMapping("/{id}")
    public Transaction updateTransaction(@PathVariable UUID id, @Valid @RequestBody TransactionRequest request) {
        return transactionService.updateTransaction(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable UUID id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }
}
