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
public class ExpenditureSummaryIntegrationTest {

    private static final String BASE_URL = "http://localhost:8811/api/v1";
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private String token;
    private String accountId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "exp_test_" + UUID.randomUUID() + "@example.com";
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
        token = mapper.readTree(loginResponse.body()).get("token").asText();

        // Create an account
        String accountJson = "{\"name\":\"Main Account\", \"type\":\"CASH\", \"initialBalance\":0.00}";
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
    void labelExpenditureBreakdownReflectsRenameAndDelete() throws Exception {
        // Create two labels
        String needsId = createLabel("NEEDS");
        String wantsId = createLabel("WANTS");

        // A labelled expense today
        createExpense(plain(25.00), needsId);
        // An unlabelled expense today
        createExpense(plain(15.00), null);

        // Summary should show per-label breakdown for today
        JsonNode summary = getSummary();
        JsonNode todayByLabel = summary.get("todayByLabel");
        assertEquals(2, todayByLabel.size(), "expected NEEDS + Unlabelled in breakdown");

        double needsAmount = amountFor(todayByLabel, "NEEDS");
        double unlabelledAmount = amountFor(todayByLabel, "Unlabelled");
        assertEquals(25.00, needsAmount, 0.01);
        assertEquals(15.00, unlabelledAmount, 0.01);

        // Rename NEEDS -> DESIRES; historical rows must be rewritten via recompute
        updateLabel(needsId, "DESIRES");
        JsonNode afterRename = getSummary().get("todayByLabel");
        assertEquals(2, afterRename.size());
        assertEquals(25.00, amountFor(afterRename, "DESIRES"), 0.01);
        assertNull(amountForNullable(afterRename, "NEEDS"), "renamed label should disappear");

        // Delete the label; its breakdown rows must be removed via recompute, and the
        // now-unlabelled transaction rolls into the Unlabelled bucket (25 + 15 = 40).
        deleteLabel(needsId);
        JsonNode afterDelete = getSummary().get("todayByLabel");
        assertNull(amountForNullable(afterDelete, "DESIRES"), "deleted label should disappear from breakdown");
        assertEquals(1, afterDelete.size());
        assertEquals(40.00, amountFor(afterDelete, "Unlabelled"), 0.01);
    }

    private JsonNode getSummary() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transactions/expenditure-summary"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        return mapper.readTree(resp.body());
    }

    private void createExpense(String amount, String labelId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transactions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(labelId == null
                        ? "{\"amount\":" + amount + ",\"transactionDate\":\"2026-09-06T10:00:00Z\",\"description\":\"exp\",\"type\":\"EXPENSE\",\"accountId\":\"" + accountId + "\"}"
                        : "{\"amount\":" + amount + ",\"transactionDate\":\"2026-09-06T10:00:00Z\",\"description\":\"exp\",\"type\":\"EXPENSE\",\"accountId\":\"" + accountId + "\",\"labelIds\":[\"" + labelId + "\"]}"))
                .build();
        assertEquals(200, client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode(), "expense create failed");
    }

    private String createLabel(String name) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/labels"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"" + name + "\"}"))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        return mapper.readTree(resp.body()).get("id").asText();
    }

    private void updateLabel(String id, String name) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/labels/" + id))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString("{\"name\":\"" + name + "\"}"))
                .build();
        assertEquals(200, client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private void deleteLabel(String id) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/labels/" + id))
                .header("Authorization", "Bearer " + token)
                .DELETE()
                .build();
        assertEquals(204, client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private double amountFor(JsonNode breakdown, String labelName) {
        Double value = amountForNullable(breakdown, labelName);
        assertNotNull(value, "expected label " + labelName + " in breakdown");
        return value;
    }

    private Double amountForNullable(JsonNode breakdown, String labelName) {
        for (JsonNode node : breakdown) {
            if (labelName.equals(node.get("labelName").asText())) {
                return node.get("amount").asDouble();
            }
        }
        return null;
    }

    private String plain(double value) {
        return java.math.BigDecimal.valueOf(value).toPlainString();
    }
}
