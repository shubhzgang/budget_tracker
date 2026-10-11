package com.budget.tracker.service;

import com.budget.tracker.model.ApiToken;
import com.budget.tracker.repository.ApiTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ApiTokenService {

    public static final String TOKEN_PREFIX = "bt_ro_";
    public static final int PREVIEW_LENGTH = 10;
    private static final Set<Integer> ALLOWED_EXPIRY_DAYS = Set.of(30, 60, 90, 360);

    private final ApiTokenRepository apiTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiTokenService(ApiTokenRepository apiTokenRepository) {
        this.apiTokenRepository = apiTokenRepository;
    }

    @Transactional
    public String generateToken(UUID userId, Integer expiryDays) {
        if (expiryDays == null || !ALLOWED_EXPIRY_DAYS.contains(expiryDays)) {
            throw new IllegalArgumentException("expiryDays must be one of: 30, 60, 90, 360");
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = TOKEN_PREFIX + HexFormat.of().formatHex(randomBytes);

        // Delete + flush before insert: the flush guarantees the delete reaches the DB
        // before the new row, so this request's own delete/insert never violates the
        // user_id UNIQUE constraint (two concurrent first-time generations can still
        // race — that surfaces as a 409 via the global handler). Inserting a fresh row
        // also gives the new token its own created_at.
        apiTokenRepository.deleteByUserId(userId);
        apiTokenRepository.flush();

        ApiToken apiToken = new ApiToken();
        apiToken.setUserId(userId);
        apiToken.setTokenHash(sha256Hex(rawToken));
        apiToken.setTokenPreview(rawToken.substring(0, PREVIEW_LENGTH));
        apiToken.setExpiresAt(OffsetDateTime.now().plusDays(expiryDays));
        apiTokenRepository.save(apiToken);

        return rawToken;
    }

    public Optional<ApiToken> getTokenInfo(UUID userId) {
        return apiTokenRepository.findByUserId(userId);
    }

    @Transactional
    public void revokeToken(UUID userId) {
        apiTokenRepository.deleteByUserId(userId);
    }

    public Optional<ApiToken> authenticateToken(String rawToken) {
        return apiTokenRepository.findByTokenHash(sha256Hex(rawToken))
                .filter(apiToken -> !apiToken.isExpired());
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
