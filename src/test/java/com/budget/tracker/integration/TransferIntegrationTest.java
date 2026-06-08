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
public class TransferIntegrationTest {

    private static final String BASE_URL = "http://localhost:8811/api/v1";
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private String token;
    private String fromAccountId;
    private String toAccountId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "transfer_int_test_" + UUID.randomUUID() + "@example.com";
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

        // Create Source Account (BANK, ₹1000)
        String fromAccountJson = "{\"name\":\"Bank Account\", \"type\":\"BANK\", \"initialBalance\":1000.00}";
        HttpRequest createFromAccountRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(fromAccountJson))
                .build();
        HttpResponse<String> fromAccountResponse = client.send(createFromAccountRequest, HttpResponse.BodyHandlers.ofString());
        fromAccountId = mapper.readTree(fromAccountResponse.body()).get("id").asText();

        // Create Destination Account (CREDIT_CARD, ₹500 outstanding debt)
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
    void testTransferWithAdjustment() throws Exception {
        // Create transfer from Bank to CC: fromAmount=95, adjustment=5 -> toAmount=100
        String transferJson = String.format("{" +
                "\"fromAccountId\":\"%s\"," +
                "\"toAccountId\":\"%s\"," +
                "\"fromAmount\":95.00," +
                "\"adjustment\":5.00," +
                "\"transactionDate\":\"2026-05-21T10:00:00Z\"," +
                "\"description\":\"Paying CC Bill with discount\"" +
                "}", fromAccountId, toAccountId);

        HttpRequest transferRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transfers"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(transferJson))
                .build();

        HttpResponse<String> transferResponse = client.send(transferRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, transferResponse.statusCode());
        JsonNode transfer = mapper.readTree(transferResponse.body());
        assertEquals(95.00, transfer.get("fromAmount").asDouble());
        assertEquals(5.00, transfer.get("adjustment").asDouble());
        assertEquals(100.00, transfer.get("toAmount").asDouble());

        // Verify balances:
        // Bank: 1000 - 95 = 905
        verifyAccountBalance(fromAccountId, 905.00);
        // CC debt decreases: 500 - 100 = 400
        verifyAccountBalance(toAccountId, 400.00);
    }

    @Test
    void testTransferWithFromAndToAmounts() throws Exception {
        // Create transfer: fromAmount=90, toAmount=100 -> adjustment=10 computed
        String transferJson = String.format("{" +
                "\"fromAccountId\":\"%s\"," +
                "\"toAccountId\":\"%s\"," +
                "\"fromAmount\":90.00," +
                "\"toAmount\":100.00," +
                "\"transactionDate\":\"2026-05-21T10:00:00Z\"," +
                "\"description\":\"Paying CC Bill\"" +
                "}", fromAccountId, toAccountId);

        HttpRequest transferRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transfers"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(transferJson))
                .build();

        HttpResponse<String> transferResponse = client.send(transferRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, transferResponse.statusCode());
        JsonNode transfer = mapper.readTree(transferResponse.body());
        assertEquals(90.00, transfer.get("fromAmount").asDouble());
        assertEquals(10.00, transfer.get("adjustment").asDouble());
        assertEquals(100.00, transfer.get("toAmount").asDouble());

        // Verify balances:
        // Bank: 1000 - 90 = 910
        verifyAccountBalance(fromAccountId, 910.00);
        // CC debt decreases: 500 - 100 = 400
        verifyAccountBalance(toAccountId, 400.00);
    }

    @Test
    void testTransferCRUD() throws Exception {
        // Create
        String transferJson = String.format("{" +
                "\"fromAccountId\":\"%s\"," +
                "\"toAccountId\":\"%s\"," +
                "\"fromAmount\":100.00," +
                "\"adjustment\":0.00," +
                "\"transactionDate\":\"2026-05-21T10:00:00Z\"," +
                "\"description\":\"Standard Transfer\"" +
                "}", fromAccountId, toAccountId);

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transfers"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(transferJson))
                .build();

        HttpResponse<String> createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, createResponse.statusCode());
        JsonNode transfer = mapper.readTree(createResponse.body());
        String transferId = transfer.get("id").asText();

        // Update
        String updateJson = String.format("{" +
                "\"fromAccountId\":\"%s\"," +
                "\"toAccountId\":\"%s\"," +
                "\"fromAmount\":150.00," +
                "\"adjustment\":0.00," +
                "\"transactionDate\":\"2026-05-21T10:00:00Z\"," +
                "\"description\":\"Updated Description\"" +
                "}", fromAccountId, toAccountId);

        HttpRequest updateRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transfers/" + transferId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString(updateJson))
                .build();

        HttpResponse<String> updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateResponse.statusCode());
        verifyAccountBalance(fromAccountId, 850.00); // 1000 - 150 = 850

        // Delete
        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transfers/" + transferId))
                .header("Authorization", "Bearer " + token)
                .DELETE()
                .build();

        HttpResponse<String> deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(204, deleteResponse.statusCode());

        // Verify balances reverted
        verifyAccountBalance(fromAccountId, 1000.00);
    }

    private void verifyAccountBalance(String id, double expected) throws Exception {
        HttpRequest getAccountRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts/" + id))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = client.send(getAccountRequest, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        JsonNode account = mapper.readTree(body);
        assertEquals(expected, account.get("balance").asDouble(), 0.01, "Balance mismatch for account " + id + ". Body: " + body);
    }
}
