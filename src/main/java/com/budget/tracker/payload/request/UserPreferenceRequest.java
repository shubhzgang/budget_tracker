package com.budget.tracker.payload.request;

import com.budget.tracker.model.TransactionType;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UserPreferenceRequest {
    private UUID defaultAccountId;
    private TransactionType defaultTransactionType;
    private UUID defaultCategoryId;
    private UUID defaultLabelId;
}
