package com.budget.tracker.controller;

import com.budget.tracker.model.ApiToken;
import com.budget.tracker.payload.request.ApiTokenRequest;
import com.budget.tracker.payload.response.ApiTokenInfoResponse;
import com.budget.tracker.payload.response.ApiTokenResponse;
import com.budget.tracker.service.ApiTokenService;
import com.budget.tracker.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-tokens")
public class ApiTokenController {

    private final ApiTokenService apiTokenService;

    public ApiTokenController(ApiTokenService apiTokenService) {
        this.apiTokenService = apiTokenService;
    }

    @PostMapping
    public ResponseEntity<ApiTokenResponse> generateToken(@RequestBody ApiTokenRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        String rawToken = apiTokenService.generateToken(userId, request.getExpiryDays());
        return ResponseEntity.ok(ApiTokenResponse.builder().token(rawToken).build());
    }

    @GetMapping
    public ResponseEntity<?> getTokenInfo() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return apiTokenService.getTokenInfo(userId)
                .<ResponseEntity<?>>map(apiToken -> ResponseEntity.ok(mapToInfoResponse(apiToken)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping
    public ResponseEntity<Void> revokeToken() {
        UUID userId = SecurityUtils.getCurrentUserId();
        apiTokenService.revokeToken(userId);
        return ResponseEntity.noContent().build();
    }

    private ApiTokenInfoResponse mapToInfoResponse(ApiToken apiToken) {
        return ApiTokenInfoResponse.builder()
                .tokenPreview(apiToken.getTokenPreview())
                .createdAt(apiToken.getCreatedAt())
                .expiresAt(apiToken.getExpiresAt())
                .expired(apiToken.isExpired())
                .build();
    }
}
