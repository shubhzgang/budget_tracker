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
public class CategoryIntegrationTest {

    private static final String BASE_URL = "http://localhost:8080/api/v1";
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String email = "cat_test_" + UUID.randomUUID() + "@example.com";
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
    }

    @Test
    void testCategoryCRUD() throws Exception {
        // 1. Create Category
        String catJson = "{\"name\":\"Groceries\", \"icon\":\"🛒\"}";
        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/categories"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(catJson))
                .build();

        HttpResponse<String> createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, createResponse.statusCode());
        JsonNode createdCat = mapper.readTree(createResponse.body());
        String catId = createdCat.get("id").asText();
        assertEquals("Groceries", createdCat.get("name").asText());

        // 2. Get All Categories (Should include defaults + the one we created)
        HttpRequest listRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/categories"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listResponse.statusCode());
        assertTrue(mapper.readTree(listResponse.body()).size() >= 4); // 3 defaults + 1 custom

        // 3. Update Category
        String updateJson = "{\"name\":\"Food & Drinks\", \"icon\":\"🍕\"}";
        HttpRequest updateRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/categories/" + catId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString(updateJson))
                .build();
        HttpResponse<String> updateResponse = client.send(updateRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateResponse.statusCode());
        assertEquals("Food & Drinks", mapper.readTree(updateResponse.body()).get("name").asText());

        // 4. Delete Category
        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/categories/" + catId))
                .header("Authorization", "Bearer " + token)
                .DELETE()
                .build();
        assertEquals(204, client.send(deleteRequest, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
