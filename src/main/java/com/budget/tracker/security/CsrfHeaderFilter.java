package com.budget.tracker.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

public class CsrfHeaderFilter extends OncePerRequestFilter {

    public static final String HX_REQUEST_HEADER = "HX-Request";

    private static final Set<String> PROTECTED_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
    private static final Set<String> EXEMPT_PATHS = Set.of("/login", "/register", "/logout");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!PROTECTED_METHODS.contains(request.getMethod().toUpperCase())) {
            return true;
        }
        String path = request.getRequestURI();
        return EXEMPT_PATHS.contains(path) || path.startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        boolean bearerToken = StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ");
        boolean hxRequest = "true".equalsIgnoreCase(request.getHeader(HX_REQUEST_HEADER));

        if (bearerToken || hxRequest) {
            filterChain.doFilter(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "CSRF check failed: state-changing requests require an HTMX request or Bearer token");
        }
    }
}
