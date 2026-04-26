package com.budget.tracker.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final int expirationMs = 60000;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", secret);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", expirationMs);
    }

    @Test
    void generateJwtToken_shouldCreateValidToken() {
        String username = "testuser";
        String token = jwtUtils.generateJwtToken(username);

        assertNotNull(token);
        assertTrue(jwtUtils.validateJwtToken(token));
        assertEquals(username, jwtUtils.getUserNameFromJwtToken(token));
    }

    @Test
    void validateJwtToken_shouldReturnFalseForInvalidToken() {
        assertFalse(jwtUtils.validateJwtToken("invalid-token"));
    }

    @Test
    void validateJwtToken_shouldReturnFalseForExpiredToken() {
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", -1000); // Expired 1 second ago
        String token = jwtUtils.generateJwtToken("testuser");
        assertFalse(jwtUtils.validateJwtToken(token));
    }
}
