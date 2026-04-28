package com.budget.tracker.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
public class HealthIntegrationTest {

    private static final String BASE_URL = "http://localhost:8080";
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void testHealthEndpointIsUp() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("%s/actuator/health", BASE_URL)))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "Health endpoint should return 200 OK");
            assertTrue(response.body().contains("\"status\":\"UP\""), "Response body should indicate status is UP");
        } catch (Exception e) {
            fail("Health endpoint is not reachable at " + BASE_URL + "/actuator/health: " + e.getMessage());
        }
    }
}