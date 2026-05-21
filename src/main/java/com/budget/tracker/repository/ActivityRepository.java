package com.budget.tracker.repository;

import com.budget.tracker.model.ActivityItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityRepository extends JpaRepository<ActivityItem, UUID> {

    @Query(value = "SELECT a FROM ActivityItem a LEFT JOIN FETCH a.account LEFT JOIN FETCH a.toAccount LEFT JOIN FETCH a.category LEFT JOIN FETCH a.label WHERE a.userId = :userId",
            countQuery = "SELECT count(a) FROM ActivityItem a WHERE a.userId = :userId")
    Page<ActivityItem> findAllByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = "SELECT a FROM ActivityItem a LEFT JOIN FETCH a.account LEFT JOIN FETCH a.toAccount LEFT JOIN FETCH a.category LEFT JOIN FETCH a.label " +
            "WHERE a.userId = :userId " +
            "AND (cast(:searchTerm as string) IS NULL OR :searchTerm = '' " +
            "OR LOWER(a.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(a.category.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(a.label.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "AND (cast(:type as string) IS NULL OR a.type = :type) " +
            "AND (cast(:startDate as string) IS NULL OR a.transactionDate >= :startDate) " +
            "AND (cast(:endDate as string) IS NULL OR a.transactionDate <= :endDate)",
            countQuery = "SELECT count(a) FROM ActivityItem a WHERE a.userId = :userId " +
            "AND (cast(:searchTerm as string) IS NULL OR :searchTerm = '' " +
            "OR LOWER(a.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(a.category.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(a.label.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "AND (cast(:type as string) IS NULL OR a.type = :type) " +
            "AND (cast(:startDate as string) IS NULL OR a.transactionDate >= :startDate) " +
            "AND (cast(:endDate as string) IS NULL OR a.transactionDate <= :endDate)")
    Page<ActivityItem> searchActivity(
            @Param("userId") UUID userId,
            @Param("searchTerm") String searchTerm,
            @Param("type") String type,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable);

    @Query("SELECT a FROM ActivityItem a LEFT JOIN FETCH a.account LEFT JOIN FETCH a.toAccount LEFT JOIN FETCH a.category LEFT JOIN FETCH a.label " +
            "WHERE (a.account.id = :accountId OR a.toAccount.id = :accountId) AND a.userId = :userId")
    List<ActivityItem> findByAccountId(@Param("userId") UUID userId, @Param("accountId") UUID accountId);
}
