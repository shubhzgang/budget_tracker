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
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

        @Query("SELECT DISTINCT t FROM Transaction t LEFT JOIN FETCH t.account LEFT JOIN FETCH t.category LEFT JOIN FETCH t.labels WHERE t.id = :id AND t.userId = :userId")
        Optional<Transaction> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

        @Query("SELECT DISTINCT t FROM Transaction t LEFT JOIN FETCH t.account LEFT JOIN FETCH t.category LEFT JOIN FETCH t.labels WHERE t.userId = :userId")
        List<Transaction> findAllByUserId(@Param("userId") UUID userId);

        @Query("SELECT DISTINCT t FROM Transaction t LEFT JOIN FETCH t.account LEFT JOIN FETCH t.category LEFT JOIN FETCH t.labels " +
                        "WHERE t.account.id = :accountId " +
                        "AND t.userId = :userId")
        List<Transaction> findAccountTransactions(@Param("accountId") UUID accountId, @Param("userId") UUID userId);

        @Query("SELECT DISTINCT t FROM Transaction t LEFT JOIN FETCH t.account LEFT JOIN FETCH t.category LEFT JOIN FETCH t.labels WHERE t.userId = :userId " +
                        "AND (cast(:searchTerm as string) IS NULL OR :searchTerm = '' OR LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
                        "OR LOWER(t.category.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
                        "OR EXISTS (SELECT 1 FROM t.labels lbl WHERE LOWER(lbl.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')))) " +
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
