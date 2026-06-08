/**
 * NOTE: Use 'make test-int' to run integration tests and not gradle.
 */
package com.budget.tracker.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
public class ActivityIntegrationTest {

    private static final String BASE_URL = "http://localhost:8811/api/v1";
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private String token;
    private String fromAccountId;
    private String toAccountId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "activity_int_test_" + UUID.randomUUID() + "@example.com";
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

        // Create Account 1
        String fromAccountJson = "{\"name\":\"Bank Account\", \"type\":\"BANK\", \"initialBalance\":1000.00}";
        HttpRequest createFromAccountRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(fromAccountJson))
                .build();
        HttpResponse<String> fromAccountResponse = client.send(createFromAccountRequest, HttpResponse.BodyHandlers.ofString());
        fromAccountId = mapper.readTree(fromAccountResponse.body()).get("id").asText();

        // Create Account 2
        String toAccountJson = "{\"name\":\"Credit Card\", \"type\":\"CREDIT_CARD\", \"initialBalance\":500.00}";
        HttpRequest createToAccountRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(toAccountJson))
                .build();
        HttpResponse<String> toAccountResponse = client.send(createToAccountRequest, HttpResponse.BodyHandlers.ofString());
        toAccountId = mapper.readTree(toAccountResponse.body()).get("id").asText();
    }

    @Test
    void testUnifiedActivityList() throws Exception {
        // 1. Create Transaction (Expense of 50.00)
        String txJson = String.format("{" +
                "\"amount\":50.00," +
                "\"transactionDate\":\"2026-05-21T09:00:00Z\"," +
                "\"description\":\"Dinner at restaurant\"," +
                "\"type\":\"EXPENSE\"," +
                "\"account\":{\"id\":\"%s\"}" +
                "}", fromAccountId);

        HttpRequest createTxRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transactions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(txJson))
                .build();
        HttpResponse<String> txResponse = client.send(createTxRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, txResponse.statusCode());

        // 2. Create Transfer (fromAmount=95, adjustment=5)
        String transferJson = String.format("{" +
                "\"fromAccountId\":\"%s\"," +
                "\"toAccountId\":\"%s\"," +
                "\"fromAmount\":95.00," +
                "\"adjustment\":5.00," +
                "\"transactionDate\":\"2026-05-21T10:00:00Z\"," +
                "\"description\":\"CC Bill Payment\"" +
                "}", fromAccountId, toAccountId);

        HttpRequest createTransferRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transfers"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(transferJson))
                .build();
        HttpResponse<String> transferResponse = client.send(createTransferRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, transferResponse.statusCode());

        // 3. Fetch activity list
        HttpRequest activityRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/activity?size=100"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> activityResponse = client.send(activityRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, activityResponse.statusCode());
        JsonNode content = mapper.readTree(activityResponse.body()).get("content");

        // Verify we got 2 activities
        assertEquals(2, content.size());

        // Since it sorts by transactionDate DESC, the transfer should be first
        JsonNode first = content.get(0);
        assertEquals("TRANSFER", first.get("kind").asText());
        assertEquals("TRANSFER", first.get("type").asText());
        assertEquals(95.0, first.get("fromAmount").asDouble());
        assertEquals(100.0, first.get("toAmount").asDouble());
        assertEquals(5.0, first.get("adjustment").asDouble());
        assertEquals("CC Bill Payment", first.get("description").asText());
        assertEquals(fromAccountId, first.get("account").get("id").asText());
        assertEquals(toAccountId, first.get("toAccount").get("id").asText());

        JsonNode second = content.get(1);
        assertEquals("TRANSACTION", second.get("kind").asText());
        assertEquals("EXPENSE", second.get("type").asText());
        assertEquals(50.0, second.get("amount").asDouble());
        assertTrue(second.get("fromAmount").isNull());
        assertTrue(second.get("toAccount").isNull());
        assertEquals("Dinner at restaurant", second.get("description").asText());

        // 4. Test filter by type = TRANSFER
        HttpRequest filterRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/activity?type=TRANSFER"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> filterResponse = client.send(filterRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, filterResponse.statusCode());
        JsonNode filterContent = mapper.readTree(filterResponse.body()).get("content");
        assertEquals(1, filterContent.size());
        assertEquals("TRANSFER", filterContent.get(0).get("kind").asText());

        // 5. Test search
        HttpRequest searchRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/activity?search=Dinner"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> searchResponse = client.send(searchRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, searchResponse.statusCode());
        JsonNode searchContent = mapper.readTree(searchResponse.body()).get("content");
        assertEquals(1, searchContent.size());
        assertEquals("Dinner at restaurant", searchContent.get(0).get("description").asText());
    }
}
