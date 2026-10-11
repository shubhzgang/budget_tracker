/**
 * NOTE: Use 'make test-int' to run integration tests and not gradle.
 */
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
public class ApiTokenIntegrationTest {

    private static final String BASE_URL = "http://localhost:8811/api/v1";
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private String jwt;

    @BeforeEach
    void setUp() throws Exception {
        String email = "apitoken_test_" + UUID.randomUUID() + "@example.com";
        String password = "password123";

        String signupJson = String.format("{\"email\":\"%s\", \"password\":\"%s\"}", email, password);
        HttpRequest registerRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(signupJson))
                .build();
        client.send(registerRequest, HttpResponse.BodyHandlers.ofString());

        String loginJson = String.format("{\"email\":\"%s\", \"password\":\"%s\"}", email, password);
        HttpRequest loginRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginJson))
                .build();

        HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode loginNode = mapper.readTree(loginResponse.body());
        jwt = loginNode.get("token").asText();
    }

    private HttpResponse<String> send(String method, String path, String authHeader, String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .method(method, publisher);
        if (authHeader != null) {
            builder.header("Authorization", "Bearer " + authHeader);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String generateReadOnlyToken() throws Exception {
        HttpResponse<String> response = send("POST", "/api-tokens", jwt, "{\"expiryDays\": 30}");
        assertEquals(200, response.statusCode(), "Token generation should return 200. Response: " + response.body());
        return mapper.readTree(response.body()).get("token").asText();
    }

    @Test
    void testReadOnlyTokenFlow() throws Exception {
        // 1. Generate a read-only token
        String readOnlyToken = generateReadOnlyToken();
        assertTrue(readOnlyToken.startsWith("bt_ro_"), "Token should have bt_ro_ prefix");

        // 2. GET with read-only token works
        HttpResponse<String> getResponse = send("GET", "/accounts", readOnlyToken, null);
        assertEquals(200, getResponse.statusCode(), "GET with read-only token should return 200");

        // 3. POST with read-only token is blocked with 403
        String accountJson = "{\"name\":\"Blocked Account\",\"type\":\"BANK\",\"initialBalance\":100.00}";
        HttpResponse<String> postResponse = send("POST", "/accounts", readOnlyToken, accountJson);
        assertEquals(403, postResponse.statusCode(), "POST with read-only token should return 403");
        assertTrue(postResponse.body().contains("Read-only API token cannot perform write operations"),
                "403 body should contain read-only error message. Response: " + postResponse.body());

        // 4. GET token info with JWT returns metadata, not the raw token
        HttpResponse<String> infoResponse = send("GET", "/api-tokens", jwt, null);
        assertEquals(200, infoResponse.statusCode());
        JsonNode info = mapper.readTree(infoResponse.body());
        assertEquals(readOnlyToken.substring(0, 10), info.get("tokenPreview").asText());
        assertFalse(info.get("expired").asBoolean());
        assertNotNull(info.get("expiresAt").asText());
        assertNull(info.get("token"), "Raw token must not be returned by GET");

        // 5. Revoke the token
        HttpResponse<String> deleteResponse = send("DELETE", "/api-tokens", jwt, null);
        assertEquals(204, deleteResponse.statusCode());

        // 6. Revoked token no longer authenticates
        HttpResponse<String> afterRevokeResponse = send("GET", "/accounts", readOnlyToken, null);
        assertEquals(401, afterRevokeResponse.statusCode(), "Revoked token should return 401");
    }

    @Test
    void testGeneratingNewTokenReplacesOldOne() throws Exception {
        String firstToken = generateReadOnlyToken();
        String secondToken = generateReadOnlyToken();
        assertNotEquals(firstToken, secondToken);

        HttpResponse<String> firstUsed = send("GET", "/accounts", firstToken, null);
        assertEquals(401, firstUsed.statusCode(), "Replaced token should no longer work");

        HttpResponse<String> secondUsed = send("GET", "/accounts", secondToken, null);
        assertEquals(200, secondUsed.statusCode(), "Newest token should work");
    }

    @Test
    void testInvalidExpiryDaysRejected() throws Exception {
        HttpResponse<String> response = send("POST", "/api-tokens", jwt, "{\"expiryDays\": 45}");
        assertEquals(400, response.statusCode(), "Invalid expiryDays should return 400");
    }

    @Test
    void testReadOnlyTokenCannotManageTokens() throws Exception {
        String readOnlyToken = generateReadOnlyToken();

        HttpResponse<String> deleteResponse = send("DELETE", "/api-tokens", readOnlyToken, null);
        assertEquals(403, deleteResponse.statusCode(), "Read-only token cannot revoke tokens");

        HttpResponse<String> postResponse = send("POST", "/api-tokens", readOnlyToken, "{\"expiryDays\": 30}");
        assertEquals(403, postResponse.statusCode(), "Read-only token cannot generate tokens");
    }
}
