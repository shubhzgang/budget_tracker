package com.budget.tracker.payload.response;

import com.budget.tracker.model.TransactionType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
public class UserPreferenceResponse {
    private UUID userId;
    private UUID defaultAccountId;
    private TransactionType defaultTransactionType;
    private UUID defaultCategoryId;
    private UUID defaultLabelId;
}
