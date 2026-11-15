package com.budget.tracker.service;

import com.budget.tracker.model.ApiToken;
import com.budget.tracker.model.ApiTokenScope;
import com.budget.tracker.model.User;
import com.budget.tracker.repository.ApiTokenRepository;
import com.budget.tracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApiTokenServiceDbTest {

    @Autowired
    private ApiTokenService apiTokenService;

    @Autowired
    private ApiTokenRepository apiTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void regenerateReplacesRowAndGivesFreshCreatedAt() {
        User user = new User();
        user.setEmail("api-token-db-" + UUID.randomUUID() + "@test.com");
        user.setPasswordHash("hash");
        userRepository.save(user);
        UUID userId = user.getId();

        String firstToken = apiTokenService.generateToken(userId, ApiTokenScope.READ_ONLY, 30);
        ApiToken first = apiTokenRepository.findByUserIdAndScope(userId, ApiTokenScope.READ_ONLY).orElseThrow();

        String secondToken = apiTokenService.generateToken(userId, ApiTokenScope.READ_ONLY, 30);
        ApiToken second = apiTokenRepository.findByUserIdAndScope(userId, ApiTokenScope.READ_ONLY).orElseThrow();

        assertNotEquals(firstToken, secondToken);
        assertNotEquals(first.getId(), second.getId(), "Regenerate must insert a new row");
        assertFalse(second.getCreatedAt().isBefore(first.getCreatedAt()),
                "created_at of the regenerated token must not be older than the original");
        assertTrue(apiTokenService.authenticateToken(secondToken).isPresent());
        assertTrue(apiTokenService.authenticateToken(firstToken).isEmpty(), "Old token must stop working");
    }

    @Test
    void readOnlyAndFullAccessTokensCoexist() {
        User user = new User();
        user.setEmail("api-token-db-" + UUID.randomUUID() + "@test.com");
        user.setPasswordHash("hash");
        userRepository.save(user);
        UUID userId = user.getId();

        String readOnly = apiTokenService.generateToken(userId, ApiTokenScope.READ_ONLY, 30);
        String fullAccess = apiTokenService.generateToken(userId, ApiTokenScope.FULL_ACCESS, 30);
        String fullAccessAgain = apiTokenService.generateToken(userId, ApiTokenScope.FULL_ACCESS, 30);

        assertTrue(apiTokenService.authenticateToken(readOnly).isPresent(), "Regenerating full-access must not touch read-only");
        assertTrue(apiTokenService.authenticateToken(fullAccess).isEmpty());
        assertEquals(ApiTokenScope.FULL_ACCESS, apiTokenService.authenticateToken(fullAccessAgain).orElseThrow().getScope());

        apiTokenService.revokeToken(userId, ApiTokenScope.FULL_ACCESS);
        assertTrue(apiTokenService.authenticateToken(fullAccessAgain).isEmpty());
        assertTrue(apiTokenService.authenticateToken(readOnly).isPresent());
    }
}
