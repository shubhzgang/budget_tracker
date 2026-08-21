package com.budget.tracker.repository;

import com.budget.tracker.model.ExpenditurePeriodTotal;
import com.budget.tracker.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenditurePeriodTotalRepository extends JpaRepository<ExpenditurePeriodTotal, UUID> {

    Optional<ExpenditurePeriodTotal> findByUserIdAndPeriodTypeAndPeriodKey(UUID userId, String periodType, String periodKey);

    List<ExpenditurePeriodTotal> findAllByUserId(UUID userId);

    void deleteAllByUserId(UUID userId);

    @Query("SELECT t.transactionDate, t.amount FROM Transaction t WHERE t.userId = :userId AND t.type IN (:types)")
    List<Object[]> findExpenditureDateAmounts(@Param("userId") UUID userId, @Param("types") List<TransactionType> types);

    @Query("SELECT " +
            "SUM(CASE WHEN t.transactionDate >= :yesterdayStart AND t.transactionDate < :todayStart THEN t.amount ELSE 0 END), " +
            "SUM(CASE WHEN t.transactionDate >= :todayStart AND t.transactionDate < :todayEnd THEN t.amount ELSE 0 END) " +
            "FROM Transaction t WHERE t.userId = :userId AND t.type IN (:types)")
    List<Object[]> sumDayTotals(@Param("userId") UUID userId,
                                @Param("types") List<TransactionType> types,
                                @Param("yesterdayStart") OffsetDateTime yesterdayStart,
                                @Param("todayStart") OffsetDateTime todayStart,
                                @Param("todayEnd") OffsetDateTime todayEnd);
}
