package com.budget.tracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "api_tokens", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "scope"}))
@Getter
@Setter
public class ApiToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private ApiTokenScope scope;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "token_preview", nullable = false)
    private String tokenPreview;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Transient
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(OffsetDateTime.now());
    }
}
