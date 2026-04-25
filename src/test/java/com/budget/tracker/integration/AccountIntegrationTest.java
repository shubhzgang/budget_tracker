package com.budget.tracker.integration;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.junit.jupiter.api.Assertions.*;

public class AccountIntegrationTest {

    private static final String BASE_URL = "http://localhost:8080";
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void testBackendIsUp() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/"))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertTrue(response.statusCode() < 500, "Server should be up and responding with < 500. Received: " + response.statusCode());
        } catch (Exception e) {
            fail("Server is not reachable at " + BASE_URL + ": " + e.getMessage());
        }
    }
}
