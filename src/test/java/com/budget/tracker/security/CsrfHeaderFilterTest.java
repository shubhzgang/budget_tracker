package com.budget.tracker.security;

import com.budget.tracker.payload.request.LoginRequest;
import com.budget.tracker.payload.request.SignupRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class CsrfHeaderFilterTest {

    private static final String CATEGORY_BODY = "{\"name\":\"Cat-Blocked\",\"icon\":\"\\uD83C\\uDF54\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void registerAndLogin() throws Exception {
        String email = "csrf@test.com";
        String password = "password123";

        SignupRequest signup = new SignupRequest();
        signup.setEmail(email);
        signup.setPassword(password);
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isOk());

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword(password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void blocksStateChangingPostAuthenticatedByCookieOnly() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                .cookie(new Cookie("jwt", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CATEGORY_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void blocksStateChangingDeleteAuthenticatedByCookieOnly() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/00000000-0000-0000-0000-000000000001")
                .cookie(new Cookie("jwt", token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsStateChangingPostWithHxRequestHeaderAndCookie() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                .cookie(new Cookie("jwt", token))
                .header(CsrfHeaderFilter.HX_REQUEST_HEADER, "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cat-HX\",\"icon\":\"\\uD83C\\uDF54\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void allowsStateChangingPostWithBearerTokenAndNoHxHeader() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cat-Bearer\",\"icon\":\"\\uD83C\\uDF54\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void allowsReadRequestsAuthenticatedByCookieWithoutHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/categories")
                .cookie(new Cookie("jwt", token)))
                .andExpect(status().isOk());
    }

    @Test
    void exemptsPlainLoginFormPostWithoutHeaders() throws Exception {
        mockMvc.perform(post("/login").param("email", "csrf@test.com").param("password", "wrong"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }
}
