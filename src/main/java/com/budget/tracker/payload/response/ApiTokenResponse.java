package com.budget.tracker.payload.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ApiTokenResponse {
    private String token;
}
