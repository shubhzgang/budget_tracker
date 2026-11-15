package com.budget.tracker.repository;

import com.budget.tracker.model.ApiToken;
import com.budget.tracker.model.ApiTokenScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiTokenRepository extends JpaRepository<ApiToken, UUID> {
    Optional<ApiToken> findByTokenHash(String tokenHash);
    Optional<ApiToken> findByUserIdAndScope(UUID userId, ApiTokenScope scope);

    @Modifying
    @Query("DELETE FROM ApiToken t WHERE t.userId = :userId AND t.scope = :scope")
    void deleteByUserIdAndScope(@Param("userId") UUID userId, @Param("scope") ApiTokenScope scope);
}
