package com.budget.tracker.security;

import com.budget.tracker.payload.response.MessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

public class ReadOnlyTokenFilter extends OncePerRequestFilter {

    // NOTE: a read-only token can call every GET endpoint, including HTML/HTMX views.
    // Any new GET endpoint must not change state or issue credentials (e.g. an OAuth
    // /authorize or refresh endpoint must reject ROLE_READ_ONLY), or a read-only token
    // could be used to obtain a full-access token.

    public static final String READ_ONLY_AUTHORITY = "ROLE_READ_ONLY";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final ObjectMapper objectMapper;

    public ReadOnlyTokenFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> READ_ONLY_AUTHORITY.equals(authority.getAuthority()));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (!SAFE_METHODS.contains(request.getMethod().toUpperCase())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    new MessageResponse("Read-only API token cannot perform write operations")));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
