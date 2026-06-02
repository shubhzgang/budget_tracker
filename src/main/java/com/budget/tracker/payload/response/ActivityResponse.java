package com.budget.tracker.payload.response;

import com.budget.tracker.model.Account;
import com.budget.tracker.model.Category;
import com.budget.tracker.model.Label;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class ActivityResponse {
    private UUID id;
    private String kind;
    private String type;
    private BigDecimal amount;
    private BigDecimal fromAmount;
    private BigDecimal toAmount;
    private BigDecimal adjustment;
    private String description;
    private OffsetDateTime transactionDate;
    private Account account;
    private Account toAccount;
    private Category category;
    private Set<Label> labels = new HashSet<>();
    private OffsetDateTime createdAt;
}
