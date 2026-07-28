package com.budget.tracker.payload.request;

import com.budget.tracker.model.TransactionType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class TransactionRequest {

    @NotNull
    private UUID accountId;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private TransactionType type;

    @NotNull
    private OffsetDateTime transactionDate;

    private String description;

    private UUID categoryId;

    private List<UUID> labelIds;
}
