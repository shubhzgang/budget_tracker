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
import java.util.List;

public class FullAccessTokenFilter extends OncePerRequestFilter {

    // A full-access token is meant for an assistant or the MCP server, so it may only reach
    // the budget data APIs. Login, API token management, backups (import/clear) and the HTML
    // views stay out of reach, so a leaked token can't mint new tokens or wipe the account.

    public static final String FULL_ACCESS_AUTHORITY = "ROLE_API_FULL_ACCESS";

    private static final List<String> ALLOWED_PATHS = List.of(
            "/api/v1/accounts",
            "/api/v1/transactions",
            "/api/v1/transfers",
            "/api/v1/activity",
            "/api/v1/categories",
            "/api/v1/labels");

    private final ObjectMapper objectMapper;

    public FullAccessTokenFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> FULL_ACCESS_AUTHORITY.equals(authority.getAuthority()));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (ALLOWED_PATHS.stream().noneMatch(allowed -> path.equals(allowed) || path.startsWith(allowed + "/"))) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    new MessageResponse("Full-access API token cannot access this endpoint")));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
