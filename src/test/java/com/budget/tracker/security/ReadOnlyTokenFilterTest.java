package com.budget.tracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReadOnlyTokenFilterTest {

    private ReadOnlyTokenFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ReadOnlyTokenFilter(new ObjectMapper());
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWithAuthorities(List<SimpleGrantedAuthority> authorities) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("principal", null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void blocksPost_ForReadOnlyPrincipal() throws Exception {
        authenticateWithAuthorities(List.of(new SimpleGrantedAuthority(ReadOnlyTokenFilter.READ_ONLY_AUTHORITY)));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest(), "Filter chain should not have been called");
        assertTrue(response.getContentAsString().contains("Read-only API token cannot perform write operations"));
    }

    @Test
    void blocksPutPatchDelete_ForReadOnlyPrincipal() throws Exception {
        authenticateWithAuthorities(List.of(new SimpleGrantedAuthority(ReadOnlyTokenFilter.READ_ONLY_AUTHORITY)));
        for (String method : List.of("PUT", "PATCH", "DELETE")) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/accounts/1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertEquals(403, response.getStatus(), method + " should be blocked");
            assertNull(chain.getRequest(), method + " should not reach the filter chain");
        }
    }

    @Test
    void allowsGetHeadOptions_ForReadOnlyPrincipal() throws Exception {
        authenticateWithAuthorities(List.of(new SimpleGrantedAuthority(ReadOnlyTokenFilter.READ_ONLY_AUTHORITY)));
        for (String method : List.of("GET", "HEAD", "OPTIONS")) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/accounts");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus(), method + " should be allowed");
            assertNotNull(chain.getRequest(), method + " should reach the filter chain");
        }
    }

    @Test
    void allowsWrites_ForFullAccessPrincipal() throws Exception {
        authenticateWithAuthorities(List.of());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    void allowsWrites_ForUnauthenticatedRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }
}
