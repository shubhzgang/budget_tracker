package com.budget.tracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
public class Account extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AccountType type;

    @Column(name = "initial_balance", nullable = false)
    private BigDecimal initialBalance;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance;

    @Column(name = "credit_limit")
    private BigDecimal creditLimit;

    @Column(name = "user_id", nullable = false)
    private UUID userId;
}
