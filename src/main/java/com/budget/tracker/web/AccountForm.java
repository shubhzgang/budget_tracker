package com.budget.tracker.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AccountForm {

    public enum LendingDirection {
        THEY_OWE_ME, I_OWE_THEM
    }

    @NotBlank
    private String name;

    @NotNull
    private com.budget.tracker.model.AccountType type;

    @NotNull
    private BigDecimal initialBalance;

    private BigDecimal creditLimit;

    private LendingDirection lendingDirection = LendingDirection.THEY_OWE_ME;
}
