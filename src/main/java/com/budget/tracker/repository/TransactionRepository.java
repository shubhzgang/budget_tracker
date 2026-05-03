package com.budget.tracker.repository;

import com.budget.tracker.model.Transaction;
import com.budget.tracker.model.TransactionType;
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
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findAllByUserId(UUID userId);
    List<Transaction> findAllByAccountIdAndUserId(UUID accountId, UUID userId);

    @Query("SELECT t FROM Transaction t LEFT JOIN t.category c LEFT JOIN t.label l WHERE t.userId = :userId " +
            "AND (cast(:searchTerm as string) IS NULL OR :searchTerm = '' OR LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(l.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "AND (cast(:type as string) IS NULL OR t.type = :type) " +
            "AND (cast(:startDate as string) IS NULL OR t.transactionDate >= :startDate) " +
            "AND (cast(:endDate as string) IS NULL OR t.transactionDate <= :endDate)")
    Page<Transaction> searchTransactions(
            @Param("userId") UUID userId,
            @Param("searchTerm") String searchTerm,
            @Param("type") TransactionType type,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable);
}
