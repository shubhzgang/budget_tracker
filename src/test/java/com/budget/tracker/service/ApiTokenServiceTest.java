package com.budget.tracker.service;

import com.budget.tracker.model.ApiToken;
import com.budget.tracker.repository.ApiTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiTokenServiceTest {

    @Mock
    private ApiTokenRepository apiTokenRepository;

    @InjectMocks
    private ApiTokenService apiTokenService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void generateToken_ReturnsPrefixedHexToken_AndStoresHashAndPreview() {
        UUID userId = UUID.randomUUID();

        String rawToken = apiTokenService.generateToken(userId, 90);

        assertTrue(rawToken.startsWith("bt_ro_"));
        assertEquals("bt_ro_".length() + 64, rawToken.length());
        assertTrue(rawToken.substring("bt_ro_".length()).matches("[0-9a-f]{64}"));

        verify(apiTokenRepository).deleteByUserId(userId);
        verify(apiTokenRepository).flush();

        ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
        verify(apiTokenRepository).save(captor.capture());
        ApiToken saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(ApiTokenService.sha256Hex(rawToken), saved.getTokenHash());
        assertEquals(rawToken.substring(0, ApiTokenService.PREVIEW_LENGTH), saved.getTokenPreview());
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getExpiresAt().isAfter(OffsetDateTime.now().plusDays(89)));
        assertTrue(saved.getExpiresAt().isBefore(OffsetDateTime.now().plusDays(91)));
    }

    @Test
    void generateToken_ReplacesExistingRowWithFreshEntity() {
        UUID userId = UUID.randomUUID();
        ApiToken existing = new ApiToken();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setTokenHash("old-hash");

        String rawToken = apiTokenService.generateToken(userId, 60);

        verify(apiTokenRepository).deleteByUserId(userId);
        verify(apiTokenRepository).flush();
        ArgumentCaptor<ApiToken> captor = ArgumentCaptor.forClass(ApiToken.class);
        verify(apiTokenRepository).save(captor.capture());
        ApiToken saved = captor.getValue();
        assertNotSame(existing, saved);
        assertNull(saved.getId(), "A new row must be inserted so created_at is fresh");
        assertEquals(ApiTokenService.sha256Hex(rawToken), saved.getTokenHash());
    }

    @Test
    void generateToken_RejectsInvalidExpiryDays() {
        UUID userId = UUID.randomUUID();

        for (Integer days : new Integer[]{null, 0, 1, 45, 361, -90}) {
            assertThrows(IllegalArgumentException.class, () -> apiTokenService.generateToken(userId, days));
        }
        verify(apiTokenRepository, never()).save(any());
    }

    @Test
    void generateToken_AcceptsAllAllowedExpiryDays() {
        UUID userId = UUID.randomUUID();
        for (Integer days : new Integer[]{30, 60, 90, 360}) {
            assertNotNull(apiTokenService.generateToken(userId, days));
        }
    }

    @Test
    void generateToken_ProducesDifferentTokensEachTime() {
        UUID userId = UUID.randomUUID();
        assertNotEquals(apiTokenService.generateToken(userId, 30), apiTokenService.generateToken(userId, 30));
    }

    @Test
    void authenticateToken_ReturnsToken_WhenFoundAndNotExpired() {
        String rawToken = "bt_ro_" + "a".repeat(64);
        ApiToken apiToken = new ApiToken();
        apiToken.setTokenHash(ApiTokenService.sha256Hex(rawToken));
        apiToken.setExpiresAt(OffsetDateTime.now().plusDays(1));
        when(apiTokenRepository.findByTokenHash(ApiTokenService.sha256Hex(rawToken))).thenReturn(Optional.of(apiToken));

        Optional<ApiToken> result = apiTokenService.authenticateToken(rawToken);

        assertTrue(result.isPresent());
        assertEquals(apiToken, result.get());
    }

    @Test
    void authenticateToken_ReturnsEmpty_WhenExpired() {
        String rawToken = "bt_ro_" + "b".repeat(64);
        ApiToken apiToken = new ApiToken();
        apiToken.setExpiresAt(OffsetDateTime.now().minusDays(1));
        when(apiTokenRepository.findByTokenHash(ApiTokenService.sha256Hex(rawToken))).thenReturn(Optional.of(apiToken));

        assertTrue(apiTokenService.authenticateToken(rawToken).isEmpty());
    }

    @Test
    void authenticateToken_ReturnsEmpty_WhenNotFound() {
        String rawToken = "bt_ro_" + "c".repeat(64);
        when(apiTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertTrue(apiTokenService.authenticateToken(rawToken).isEmpty());
    }

    @Test
    void revokeToken_DeletesByUserId() {
        UUID userId = UUID.randomUUID();
        apiTokenService.revokeToken(userId);
        verify(apiTokenRepository).deleteByUserId(userId);
    }

    @Test
    void sha256Hex_MatchesKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ApiTokenService.sha256Hex("abc"));
    }
}
