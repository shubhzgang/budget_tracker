package com.budget.tracker.payload.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class TransferRequest {
    @NotNull
    private UUID fromAccountId;

    @NotNull
    private UUID toAccountId;

    private BigDecimal fromAmount;

    private BigDecimal toAmount;

    private BigDecimal adjustment;

    @NotNull
    private OffsetDateTime transactionDate;

    @NotNull
    private String description;

    private UUID categoryId;

    private List<UUID> labelIds;

    public boolean isValid() {
        int count = 0;
        if (fromAmount != null) count++;
        if (toAmount != null) count++;
        if (adjustment != null) count++;
        return count == 2;
    }
}
