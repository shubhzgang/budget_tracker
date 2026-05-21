package com.budget.tracker.controller;

import com.budget.tracker.model.Transfer;
import com.budget.tracker.payload.request.TransferRequest;
import com.budget.tracker.payload.response.TransferResponse;
import com.budget.tracker.service.TransferService;
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
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping
    public Page<TransferResponse> getAllTransfers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return transferService.getAllTransfers(search, startDate, endDate, pageable).map(this::mapToResponse);
    }

    @GetMapping("/{id}")
    public TransferResponse getTransferById(@PathVariable UUID id) {
        return mapToResponse(transferService.getTransferById(id));
    }

    @PostMapping
    public TransferResponse createTransfer(@Valid @RequestBody TransferRequest transferRequest) {
        return mapToResponse(transferService.createTransfer(transferRequest));
    }

    @PutMapping("/{id}")
    public TransferResponse updateTransfer(@PathVariable UUID id, @Valid @RequestBody TransferRequest transferRequest) {
        return mapToResponse(transferService.updateTransfer(id, transferRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransfer(@PathVariable UUID id) {
        transferService.deleteTransfer(id);
        return ResponseEntity.noContent().build();
    }

    private TransferResponse mapToResponse(Transfer transfer) {
        TransferResponse response = new TransferResponse();
        response.setId(transfer.getId());
        response.setFromAmount(transfer.getFromAmount());
        response.setToAmount(transfer.getToAmount());
        response.setAdjustment(transfer.getAdjustment());
        response.setDescription(transfer.getDescription());
        response.setTransactionDate(transfer.getTransactionDate());
        response.setFromAccount(transfer.getFromAccount());
        response.setToAccount(transfer.getToAccount());
        response.setCategory(transfer.getCategory());
        response.setLabel(transfer.getLabel());
        response.setCreatedAt(transfer.getCreatedAt());
        return response;
    }
}
