package com.budget.tracker.payload.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiTokenRequest {
    private Integer expiryDays;
}
