package com.budget.tracker.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
public class AuthSystemIntegrationTest {

    private static final String BASE_URL = "http://localhost:8080/api/v1/auth";
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void testAuthFlow() throws Exception {
        String uniqueEmail = "user_" + UUID.randomUUID() + "@example.com";
        String password = "password123";

        // 1. Register
        String signupJson = String.format("{\"email\":\"%s\", \"password\":\"%s\"}", uniqueEmail, password);
        
        HttpRequest registerRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(signupJson))
                .build();

        HttpResponse<String> registerResponse = client.send(registerRequest, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, registerResponse.statusCode(), "Registration should return 200 OK");
        assertTrue(registerResponse.body().contains("User registered successfully!"), "Response should contain success message");

        // 2. Login
        String loginJson = String.format("{\"email\":\"%s\", \"password\":\"%s\"}", uniqueEmail, password);
        
        HttpRequest loginRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginJson))
                .build();

        HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, loginResponse.statusCode(), "Login should return 200 OK");
        assertTrue(loginResponse.body().contains("\"token\""), "Response should contain a JWT token");
        assertTrue(loginResponse.body().contains("\"email\":\"" + uniqueEmail + "\""), "Response should contain the correct email");

        // 3. Fail Login with wrong password
        String wrongLoginJson = String.format("{\"email\":\"%s\", \"password\":\"wrongpassword\"}", uniqueEmail);
        
        HttpRequest wrongLoginRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(wrongLoginJson))
                .build();

        HttpResponse<String> wrongLoginResponse = client.send(wrongLoginRequest, HttpResponse.BodyHandlers.ofString());
        
        assertEquals(401, wrongLoginResponse.statusCode(), "Login with wrong password should return 401 Unauthorized");
    }
}
