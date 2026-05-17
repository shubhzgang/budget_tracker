package com.budget.tracker.payload.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class TransferRequest {
    @NotNull
    private UUID fromAccountId;

    @NotNull
    private UUID toAccountId;

    @Positive(message = "Transfer amount must be greater than zero")
    @NotNull
    private BigDecimal amount;

    @NotNull
    private OffsetDateTime transactionDate;

    @NotNull
    private String description;

    private UUID categoryId;
}
