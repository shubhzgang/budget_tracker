package com.budget.tracker.model;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "expenditure_period_totals",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "period_type", "period_key"}))
@Getter
@Setter
public class ExpenditurePeriodTotal {

    public static final String PERIOD_WEEK = "WEEK";
    public static final String PERIOD_MONTH = "MONTH";

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "period_type", nullable = false)
    private String periodType;

    @Column(name = "period_key", nullable = false)
    private String periodKey;

    @Column(nullable = false)
    private BigDecimal total;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
