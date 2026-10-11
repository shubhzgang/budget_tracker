package com.budget.tracker.security;

import com.budget.tracker.model.ApiToken;
import com.budget.tracker.model.User;
import com.budget.tracker.repository.UserRepository;
import com.budget.tracker.service.ApiTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthTokenFilterTest {

    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private UserDetailsServiceImpl userDetailsService;
    @Mock
    private ApiTokenService apiTokenService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @Mock
    private UserDetails userDetails;

    private AuthTokenFilter tokenFilter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tokenFilter = new AuthTokenFilter(jwtUtils, userDetailsService, apiTokenService, userRepository);
        SecurityContextHolder.clearContext();
        when(userDetails.getAuthorities()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_withJwtCookie_shouldAuthenticate() throws Exception {
        String token = "cookie-jwt-token";
        String username = "user@example.com";
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("jwt", token)});
        when(jwtUtils.validateJwtToken(token)).thenReturn(true);
        when(jwtUtils.getUserNameFromJwtToken(token)).thenReturn(username);
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);

        tokenFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(userDetails, auth.getPrincipal());
        verify(jwtUtils).validateJwtToken(token);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_noTokens_shouldNotAuthenticate() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[]{});

        tokenFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withInvalidCookieToken_shouldNotAuthenticate() throws Exception {
        String token = "invalid-token";
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("jwt", token)});
        when(jwtUtils.validateJwtToken(token)).thenReturn(false);

        tokenFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withBearerHeader_shouldTakePrecedenceOverCookie() throws Exception {
        String headerToken = "bearer-token";
        String cookieToken = "cookie-token";
        String username = "user@example.com";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + headerToken);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("jwt", cookieToken)});
        when(jwtUtils.validateJwtToken(headerToken)).thenReturn(true);
        when(jwtUtils.getUserNameFromJwtToken(headerToken)).thenReturn(username);
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);

        tokenFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(userDetails, auth.getPrincipal());
        verify(jwtUtils).validateJwtToken(headerToken);
        verify(jwtUtils, never()).validateJwtToken(cookieToken);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withValidReadOnlyBearerToken_shouldAuthenticateWithReadOnlyAuthority() throws Exception {
        String token = "bt_ro_" + "a".repeat(64);
        UUID userId = UUID.randomUUID();
        ApiToken apiToken = new ApiToken();
        apiToken.setUserId(userId);
        apiToken.setExpiresAt(OffsetDateTime.now().plusDays(30));
        User user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setPasswordHash("hash");

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getCookies()).thenReturn(new Cookie[]{});
        when(apiTokenService.authenticateToken(token)).thenReturn(Optional.of(apiToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        tokenFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> ReadOnlyTokenFilter.READ_ONLY_AUTHORITY.equals(a.getAuthority())));
        verify(jwtUtils, never()).validateJwtToken(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withUnknownOrExpiredReadOnlyToken_shouldNotAuthenticate() throws Exception {
        String token = "bt_ro_" + "b".repeat(64);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getCookies()).thenReturn(new Cookie[]{});
        when(apiTokenService.authenticateToken(token)).thenReturn(Optional.empty());

        tokenFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtUtils, never()).validateJwtToken(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_withReadOnlyTokenInJwtCookie_shouldNotAuthenticate() throws Exception {
        String token = "bt_ro_" + "d".repeat(64);

        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("jwt", token)});

        tokenFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(apiTokenService, never()).authenticateToken(anyString());
        verify(jwtUtils, never()).validateJwtToken(anyString());
        verify(filterChain).doFilter(request, response);
    }
}
