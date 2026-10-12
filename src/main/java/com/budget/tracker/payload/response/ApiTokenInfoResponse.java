package com.budget.tracker.payload.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class ApiTokenInfoResponse {
    private String tokenPreview;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    private boolean expired;
}
