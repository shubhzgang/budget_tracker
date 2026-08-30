package com.budget.tracker.security;

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthTokenFilterTest {

    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private UserDetailsServiceImpl userDetailsService;
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
        tokenFilter = new AuthTokenFilter(jwtUtils, userDetailsService);
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
}
