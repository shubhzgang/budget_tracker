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
public class TransactionIntegrationTest {

    private static final String BASE_URL = "http://localhost:8080/api/v1";
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private String token;
    private String accountId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "tx_test_" + UUID.randomUUID() + "@example.com";
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

        // Create an account to use for transactions
        String accountJson = "{\"name\":\"Main Account\", \"type\":\"CASH\", \"initialBalance\":1000.00}";
        HttpRequest createAccountRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(accountJson))
                .build();
        HttpResponse<String> accountResponse = client.send(createAccountRequest, HttpResponse.BodyHandlers.ofString());
        accountId = mapper.readTree(accountResponse.body()).get("id").asText();
    }

    @Test
    void testTransactionCRUD() throws Exception {
        // 1. Create Transaction (Income)
        String txJson = String.format("{" +
                "\"amount\":500.00," +
                "\"transactionDate\":\"2026-04-27T10:00:00Z\"," +
                "\"description\":\"Salary\"," +
                "\"type\":\"INCOME\"," +
                "\"account\":{\"id\":\"%s\"}" +
                "}", accountId);

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transactions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(txJson))
                .build();

        HttpResponse<String> createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, createResponse.statusCode());
        JsonNode createdTx = mapper.readTree(createResponse.body());
        String txId = createdTx.get("id").asText();
        assertEquals(500.0, createdTx.get("amount").asDouble());

        // Verify balance updated (1000 + 500 = 1500)
        verifyBalance(1500.00);

        // 2. Get All Transactions
        HttpRequest listRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transactions"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listResponse.statusCode());
        assertTrue(mapper.readTree(listResponse.body()).get("content").size() > 0);

        // 3. Delete Transaction
        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transactions/" + txId))
                .header("Authorization", "Bearer " + token)
                .DELETE()
                .build();
        assertEquals(204, client.send(deleteRequest, HttpResponse.BodyHandlers.ofString()).statusCode());

        // Verify balance reverted (1500 - 500 = 1000)
        verifyBalance(1000.00);
    }

    @Test
    void testTransferIntegration() throws Exception {
        // Create second account
        String account2Json = "{\"name\":\"Savings\", \"type\":\"BANK\", \"initialBalance\":0.00}";
        HttpRequest createAccount2Request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(account2Json))
                .build();
        String account2Id = mapper.readTree(client.send(createAccount2Request, HttpResponse.BodyHandlers.ofString()).body()).get("id").asText();

        // Perform Transfer (Main Account -> Savings)
        String transferJson = String.format("{" +
                "\"fromAccountId\":\"%s\"," +
                "\"toAccountId\":\"%s\"," +
                "\"amount\":200.00," +
                "\"transactionDate\":\"2026-04-27T12:00:00Z\"," +
                "\"description\":\"Monthly Savings\"" +
                "}", accountId, account2Id);

        HttpRequest transferRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transactions/transfer"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(transferJson))
                .build();

        HttpResponse<String> transferResponse = client.send(transferRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, transferResponse.statusCode());

        // Verify balances
        // Main: 1000 - 200 = 800
        // Savings: 0 + 200 = 200
        verifyAccountBalance(accountId, 800.00);
        verifyAccountBalance(account2Id, 200.00);

        // Verify metadata in transaction list
        HttpRequest listRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transactions?size=100"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode content = mapper.readTree(listResponse.body()).get("content");
        
        boolean foundIncoming = false;
        boolean foundOutgoing = false;

        for (JsonNode tx : content) {
            if (tx.get("type").asText().equals("TRANSFER")) {
                if (tx.get("isIncomingTransfer").asBoolean()) {
                    foundIncoming = true;
                    assertEquals(account2Id, tx.get("account").get("id").asText());
                    assertEquals(accountId, tx.get("linkedAccount").get("id").asText());
                } else {
                    foundOutgoing = true;
                    assertEquals(accountId, tx.get("account").get("id").asText());
                    assertEquals(account2Id, tx.get("linkedAccount").get("id").asText());
                }
            }
        }
        assertTrue(foundIncoming, "Incoming transfer transaction not found");
        assertTrue(foundOutgoing, "Outgoing transfer transaction not found");
    }

    @Test
    void testUpdateTransferIntegration() throws Exception {
        // 1. Setup: Create two accounts
        String account2Json = "{\"name\":\"Savings\", \"type\":\"BANK\", \"initialBalance\":100.00}";
        HttpRequest createAccount2Request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(account2Json))
                .build();
        String account2Id = mapper.readTree(client.send(createAccount2Request, HttpResponse.BodyHandlers.ofString()).body()).get("id").asText();

        // 2. Perform Transfer (Main Account -> Savings): 50.00
        // Main: 1000 - 50 = 950
        // Savings: 100 + 50 = 150
        String transferJson = String.format("{" +
                "\"fromAccountId\":\"%s\"," +
                "\"toAccountId\":\"%s\"," +
                "\"amount\":50.00," +
                "\"transactionDate\":\"2026-04-27T12:00:00Z\"," +
                "\"description\":\"Initial Transfer\"" +
                "}", accountId, account2Id);

        HttpRequest transferRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transactions/transfer"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(transferJson))
                .build();

        HttpResponse<String> transferResponse = client.send(transferRequest, HttpResponse.BodyHandlers.ofString());
        String sourceTxId = mapper.readTree(transferResponse.body()).get("id").asText();

        verifyAccountBalance(accountId, 950.00);
        verifyAccountBalance(account2Id, 150.00);

        // 3. Update Transfer: Change amount to 150.00
        // Expected: 
        // Main: 950 (current) + 50 (revert) - 150 (new) = 850
        // Savings: 150 (current) - 50 (revert) + 150 (new) = 250
        String updateJson = "{" +
                "\"amount\":150.00," +
                "\"transactionDate\":\"2026-04-27T12:00:00Z\"," +
                "\"description\":\"Updated Transfer\"" +
                "}";

        HttpRequest updateRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transactions/" + sourceTxId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString(updateJson))
                .build();

        HttpResponse<String> updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateResponse.statusCode());

        // 4. Verify balances
        verifyAccountBalance(accountId, 850.00);
        verifyAccountBalance(account2Id, 250.00);

        // 5. Verify both transactions in list are updated
        HttpRequest listRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transactions?size=100"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode content = mapper.readTree(listResponse.body()).get("content");

        int transferCount = 0;
        for (JsonNode tx : content) {
            if (tx.get("type").asText().equals("TRANSFER")) {
                transferCount++;
                assertEquals(150.00, tx.get("amount").asDouble(), 0.01);
                assertEquals("Updated Transfer", tx.get("description").asText());
            }
        }
        assertEquals(2, transferCount, "Should have exactly two transfer records");
    }

    private void verifyBalance(double expected) throws Exception {
        verifyAccountBalance(accountId, expected);
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
