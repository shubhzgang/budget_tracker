package com.budget.tracker.payload.response;

import com.budget.tracker.model.Account;
import com.budget.tracker.model.Category;
import com.budget.tracker.model.Label;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class TransferResponse {
    private UUID id;
    private BigDecimal fromAmount;
    private BigDecimal toAmount;
    private BigDecimal adjustment;
    private String description;
    private OffsetDateTime transactionDate;
    private Account fromAccount;
    private Account toAccount;
    private Category category;
    private Label label;
    private OffsetDateTime createdAt;
}
