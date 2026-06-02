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

    @Query(value = "SELECT a FROM ActivityItem a LEFT JOIN FETCH a.account LEFT JOIN FETCH a.toAccount LEFT JOIN FETCH a.category WHERE a.userId = :userId",
            countQuery = "SELECT count(a) FROM ActivityItem a WHERE a.userId = :userId")
    Page<ActivityItem> findAllByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = "SELECT DISTINCT a FROM ActivityItem a LEFT JOIN FETCH a.account LEFT JOIN FETCH a.toAccount LEFT JOIN FETCH a.category " +
            "WHERE a.userId = :userId " +
            "AND (cast(:searchTerm as string) IS NULL OR :searchTerm = '' " +
            "OR LOWER(a.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(a.category.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR EXISTS (SELECT 1 FROM Transaction t JOIN t.labels lbl WHERE t.id = a.id AND LOWER(lbl.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "OR EXISTS (SELECT 1 FROM Transfer tr JOIN tr.labels lbl WHERE tr.id = a.id AND LOWER(lbl.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')))) " +
            "AND (cast(:type as string) IS NULL OR a.type = :type) " +
            "AND (cast(:accountId as string) IS NULL OR a.account.id = :accountId OR a.toAccount.id = :accountId) " +
            "AND (cast(:startDate as string) IS NULL OR a.transactionDate >= :startDate) " +
            "AND (cast(:endDate as string) IS NULL OR a.transactionDate <= :endDate)",
            countQuery = "SELECT count(DISTINCT a) FROM ActivityItem a WHERE a.userId = :userId " +
            "AND (cast(:searchTerm as string) IS NULL OR :searchTerm = '' " +
            "OR LOWER(a.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(a.category.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "AND (cast(:type as string) IS NULL OR a.type = :type) " +
            "AND (cast(:accountId as string) IS NULL OR a.account.id = :accountId OR a.toAccount.id = :accountId) " +
            "AND (cast(:startDate as string) IS NULL OR a.transactionDate >= :startDate) " +
            "AND (cast(:endDate as string) IS NULL OR a.transactionDate <= :endDate)")
    Page<ActivityItem> searchActivity(
            @Param("userId") UUID userId,
            @Param("searchTerm") String searchTerm,
            @Param("type") String type,
            @Param("accountId") UUID accountId,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable);

    @Query("SELECT a FROM ActivityItem a LEFT JOIN FETCH a.account LEFT JOIN FETCH a.toAccount LEFT JOIN FETCH a.category " +
            "WHERE (a.account.id = :accountId OR a.toAccount.id = :accountId) AND a.userId = :userId")
    List<ActivityItem> findByAccountId(@Param("userId") UUID userId, @Param("accountId") UUID accountId);

    @Query(value = "SELECT tl.transaction_id AS activity_id, l.id, l.name, l.is_default, l.user_id FROM transaction_labels tl JOIN labels l ON l.id = tl.label_id WHERE tl.transaction_id IN :ids", nativeQuery = true)
    List<Object[]> findLabelsForTransactions(@Param("ids") List<UUID> ids);

    @Query(value = "SELECT trl.transfer_id AS activity_id, l.id, l.name, l.is_default, l.user_id FROM transfer_labels trl JOIN labels l ON l.id = trl.label_id WHERE trl.transfer_id IN :ids", nativeQuery = true)
    List<Object[]> findLabelsForTransfers(@Param("ids") List<UUID> ids);
}
