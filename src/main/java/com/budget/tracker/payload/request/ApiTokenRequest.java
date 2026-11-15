package com.budget.tracker.payload.request;

import com.budget.tracker.model.ApiTokenScope;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiTokenRequest {
    private Integer expiryDays;
    private ApiTokenScope scope = ApiTokenScope.READ_ONLY;
}
