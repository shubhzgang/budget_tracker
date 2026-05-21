package com.budget.tracker.repository;

import com.budget.tracker.model.Transfer;
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
public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    @Query("SELECT t FROM Transfer t LEFT JOIN FETCH t.fromAccount LEFT JOIN FETCH t.toAccount LEFT JOIN FETCH t.category LEFT JOIN FETCH t.label WHERE t.userId = :userId")
    List<Transfer> findAllByUserId(@Param("userId") UUID userId);

    @Query("SELECT DISTINCT t FROM Transfer t LEFT JOIN FETCH t.fromAccount LEFT JOIN FETCH t.toAccount LEFT JOIN FETCH t.category LEFT JOIN FETCH t.label " +
            "WHERE (t.fromAccount.id = :accountId OR t.toAccount.id = :accountId) AND t.userId = :userId")
    List<Transfer> findAccountTransfers(@Param("accountId") UUID accountId, @Param("userId") UUID userId);

    @Query("SELECT DISTINCT t FROM Transfer t LEFT JOIN FETCH t.fromAccount LEFT JOIN FETCH t.toAccount LEFT JOIN FETCH t.category LEFT JOIN FETCH t.label " +
            "WHERE t.userId = :userId " +
            "AND (cast(:searchTerm as string) IS NULL OR :searchTerm = '' " +
            "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(t.category.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(t.label.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "AND (cast(:startDate as string) IS NULL OR t.transactionDate >= :startDate) " +
            "AND (cast(:endDate as string) IS NULL OR t.transactionDate <= :endDate)")
    Page<Transfer> searchTransfers(
            @Param("userId") UUID userId,
            @Param("searchTerm") String searchTerm,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate,
            Pageable pageable);
}
