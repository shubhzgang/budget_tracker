package com.budget.tracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
public class UserPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "default_account_id")
    private UUID defaultAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_transaction_type")
    private TransactionType defaultTransactionType;

    @Column(name = "default_category_id")
    private UUID defaultCategoryId;

    @Column(name = "default_label_id")
    private UUID defaultLabelId;

}
