package com.budget.tracker.security;

import com.budget.tracker.model.ApiToken;
import com.budget.tracker.model.User;
import com.budget.tracker.repository.UserRepository;
import com.budget.tracker.service.ApiTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class AuthTokenFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;
    private final ApiTokenService apiTokenService;
    private final UserRepository userRepository;

    public AuthTokenFilter(JwtUtils jwtUtils, UserDetailsServiceImpl userDetailsService,
                           ApiTokenService apiTokenService, UserRepository userRepository) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
        this.apiTokenService = apiTokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = parseJwt(request);
            if (token != null) {
                if (token.startsWith(ApiTokenService.TOKEN_PREFIX)) {
                    authenticateReadOnlyToken(token, request);
                } else if (jwtUtils.validateJwtToken(token)) {
                    String username = jwtUtils.getUserNameFromJwtToken(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    setAuthentication(userDetails, request);
                }
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateReadOnlyToken(String token, HttpServletRequest request) {
        Optional<ApiToken> apiToken = apiTokenService.authenticateToken(token);
        if (apiToken.isEmpty()) {
            return;
        }
        Optional<User> user = userRepository.findById(apiToken.get().getUserId());
        if (user.isEmpty()) {
            return;
        }
        UserDetailsImpl userDetails = UserDetailsImpl.build(user.get(),
                List.of(new SimpleGrantedAuthority(ReadOnlyTokenFilter.READ_ONLY_AUTHORITY)));
        setAuthentication(userDetails, request);
    }

    private void setAuthentication(UserDetails userDetails, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        return parseJwtFromCookie(request);
    }

    private String parseJwtFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("jwt".equals(cookie.getName())) {
                    String value = cookie.getValue();
                    // API tokens are Bearer-only; never accept them from cookies
                    return value != null && value.startsWith(ApiTokenService.TOKEN_PREFIX) ? null : value;
                }
            }
        }
        return null;
    }
}
