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
public class AccountIntegrationTest {

    private static final String BASE_URL = "http://localhost:8080/api/v1";
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String email = "account_test_" + UUID.randomUUID() + "@example.com";
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
    void testAccountCRUD() throws Exception {
        // 1. Create Account
        String accountJson = "{" +
                "\"name\":\"Test Savings\"," +
                "\"type\":\"BANK\"," +
                "\"initialBalance\":1000.00" +
                "}";

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(accountJson))
                .build();

        HttpResponse<String> createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, createResponse.statusCode(), "Create account should return 200 OK. Response: " + createResponse.body());
        
        JsonNode createdAccount = mapper.readTree(createResponse.body());
        String accountId = createdAccount.get("id").asText();
        assertEquals("Test Savings", createdAccount.get("name").asText());

        // 2. Get All Accounts
        HttpRequest listRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listResponse.statusCode());
        JsonNode accountsList = mapper.readTree(listResponse.body());
        assertTrue(accountsList.isArray());
        assertTrue(accountsList.size() > 0);

        // 3. Get Account by ID
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts/" + accountId))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResponse.statusCode());
        JsonNode fetchedAccount = mapper.readTree(getResponse.body());
        assertEquals(accountId, fetchedAccount.get("id").asText());

        // 4. Update Account
        String updateJson = "{" +
                "\"name\":\"Updated Savings Name\"," +
                "\"type\":\"BANK\"," +
                "\"initialBalance\":1200.00" +
                "}";

        HttpRequest updateRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts/" + accountId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString(updateJson))
                .build();

        HttpResponse<String> updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateResponse.statusCode());
        JsonNode updatedAccount = mapper.readTree(updateResponse.body());
        assertEquals("Updated Savings Name", updatedAccount.get("name").asText());

        // 5. Delete Account
        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts/" + accountId))
                .header("Authorization", "Bearer " + token)
                .DELETE()
                .build();

        HttpResponse<String> deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(204, deleteResponse.statusCode());

        // 6. Verify Deletion
        HttpRequest verifyRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts/" + accountId))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> verifyResponse = client.send(verifyRequest, HttpResponse.BodyHandlers.ofString());
        // GlobalExceptionHandler might return 400 for RuntimeException when account not found
        assertTrue(verifyResponse.statusCode() >= 400);
    }

    @Test
    void testCreditCardBalanceLogic() throws Exception {
        // 1. Create Credit Card Account
        String accountJson = "{" +
                "\"name\":\"Visa Integration Test\"," +
                "\"type\":\"CREDIT_CARD\"," +
                "\"initialBalance\":0.00," +
                "\"creditLimit\":5000.00" +
                "}";

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(accountJson))
                .build();

        HttpResponse<String> createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, createResponse.statusCode());
        String accountId = mapper.readTree(createResponse.body()).get("id").asText();

        // 2. Add Expense Transaction
        String txJson = String.format("{" +
                "\"amount\":250.00," +
                "\"transactionDate\":\"2026-05-01T10:00:00Z\"," +
                "\"description\":\"Dinner\"," +
                "\"type\":\"EXPENSE\"," +
                "\"account\":{\"id\":\"%s\"}" +
                "}", accountId);

        HttpRequest txRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transactions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(txJson))
                .build();

        HttpResponse<String> txResponse = client.send(txRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, txResponse.statusCode());

        // 3. Verify Balance INCREASED (debt increased)
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts/" + accountId))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResponse.statusCode());
        JsonNode account = mapper.readTree(getResponse.body());
        assertEquals(250.00, account.get("balance").asDouble(), 0.01, "Credit card balance should INCREASE after expense");
    }
}
