package com.budget.tracker.repository;

import com.budget.tracker.model.ExpenditurePeriodTotal;
import com.budget.tracker.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenditurePeriodTotalRepository extends JpaRepository<ExpenditurePeriodTotal, UUID> {

    Optional<ExpenditurePeriodTotal> findByUserIdAndPeriodTypeAndPeriodKey(UUID userId, String periodType, String periodKey);

    @Modifying
    @Query(value = "INSERT INTO expenditure_period_totals (id, user_id, period_type, period_key, total) " +
            "VALUES (CAST(:id AS uuid), CAST(:userId AS uuid), :periodType, :periodKey, :delta) " +
            "ON CONFLICT (user_id, period_type, period_key) " +
            "DO UPDATE SET total = expenditure_period_totals.total + EXCLUDED.total", nativeQuery = true)
    void adjustTotal(@Param("id") UUID id,
                     @Param("userId") UUID userId,
                     @Param("periodType") String periodType,
                     @Param("periodKey") String periodKey,
                     @Param("delta") BigDecimal delta);

    @Modifying
    @Query("DELETE FROM ExpenditurePeriodTotal t WHERE t.userId = :userId AND t.periodType = :periodType " +
            "AND t.periodKey = :periodKey AND t.total = 0")
    void deleteZeroed(@Param("userId") UUID userId,
                      @Param("periodType") String periodType,
                      @Param("periodKey") String periodKey);

    List<ExpenditurePeriodTotal> findAllByUserId(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ExpenditurePeriodTotal t WHERE t.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);

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
