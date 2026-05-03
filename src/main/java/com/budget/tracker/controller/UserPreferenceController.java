package com.budget.tracker.controller;

import com.budget.tracker.model.UserPreference;
import com.budget.tracker.payload.request.UserPreferenceRequest;
import com.budget.tracker.payload.response.UserPreferenceResponse;
import com.budget.tracker.service.UserPreferenceService;
import com.budget.tracker.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/preferences")
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    public UserPreferenceController(UserPreferenceService userPreferenceService) {
        this.userPreferenceService = userPreferenceService;
    }

    @GetMapping
    public ResponseEntity<UserPreferenceResponse> getPreferences() {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserPreference prefs = userPreferenceService.getPreferences(userId);
        return ResponseEntity.ok(mapToResponse(prefs));
    }

    @PutMapping
    public ResponseEntity<UserPreferenceResponse> updatePreferences(@RequestBody UserPreferenceRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserPreference updatedPrefs = new UserPreference();
        updatedPrefs.setDefaultAccountId(request.getDefaultAccountId());
        updatedPrefs.setDefaultTransactionType(request.getDefaultTransactionType());
        updatedPrefs.setDefaultCategoryId(request.getDefaultCategoryId());
        updatedPrefs.setDefaultLabelId(request.getDefaultLabelId());

        UserPreference savedPrefs = userPreferenceService.updatePreferences(userId, updatedPrefs);
        return ResponseEntity.ok(mapToResponse(savedPrefs));
    }

    private UserPreferenceResponse mapToResponse(UserPreference prefs) {
        return UserPreferenceResponse.builder()
                .userId(prefs.getUserId())
                .defaultAccountId(prefs.getDefaultAccountId())
                .defaultTransactionType(prefs.getDefaultTransactionType())
                .defaultCategoryId(prefs.getDefaultCategoryId())
                .defaultLabelId(prefs.getDefaultLabelId())
                .build();
    }
}
