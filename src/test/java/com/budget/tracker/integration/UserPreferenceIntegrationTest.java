package com.budget.tracker.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
public class UserPreferenceIntegrationTest {

    private static final String BASE_URL = "http://localhost:8080/api/v1";
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String email = "pref_test_" + UUID.randomUUID() + "@example.com";
        String password = "password123";

        // Register
        String signupJson = String.format("{\"email\":\"%s\", \"password\":\"%s\"}", email, password);
        HttpRequest registerRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(signupJson))
                .build();
        client.send(registerRequest, HttpResponse.BodyHandlers.ofString());

        // Login
        String loginJson = String.format("{\"email\":\"%s\", \"password\":\"%s\"}", email, password);
        HttpRequest loginRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginJson))
                .build();

        HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode loginNode = mapper.readTree(loginResponse.body());
        token = loginNode.get("token").asText();
    }

    @Test
    void testPreferenceFlow() throws Exception {
        // 1. Get default preferences
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/preferences"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResponse.statusCode());
        JsonNode getNode = mapper.readTree(getResponse.body());
        assertTrue(getNode.get("defaultTransactionType").isNull());

        // 2. Update preferences
        UUID accountId = UUID.randomUUID();
        String updateJson = String.format("{\"defaultTransactionType\":\"INCOME\", \"defaultAccountId\":\"%s\"}", accountId);
        HttpRequest putRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/preferences"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(updateJson))
                .build();

        HttpResponse<String> putResponse = client.send(putRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, putResponse.statusCode());
        JsonNode putNode = mapper.readTree(putResponse.body());
        assertEquals("INCOME", putNode.get("defaultTransactionType").asText());
        assertEquals(accountId.toString(), putNode.get("defaultAccountId").asText());

        // 3. Verify update
        HttpResponse<String> verifyResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode verifyNode = mapper.readTree(verifyResponse.body());
        assertEquals("INCOME", verifyNode.get("defaultTransactionType").asText());
    }
}
