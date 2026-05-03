package com.budget.tracker.service;

import com.budget.tracker.model.UserPreference;
import com.budget.tracker.repository.UserPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserPreferenceService {

    private final UserPreferenceRepository userPreferenceRepository;

    public UserPreferenceService(UserPreferenceRepository userPreferenceRepository) {
        this.userPreferenceRepository = userPreferenceRepository;
    }

    @Transactional(readOnly = true)
    public UserPreference getPreferences(UUID userId) {
        return userPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserPreference prefs = new UserPreference();
                    prefs.setUserId(userId);
                    return prefs;
                });
    }

    @Transactional
    public UserPreference updatePreferences(UUID userId, UserPreference updatedPrefs) {
        UserPreference existingPrefs = userPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserPreference prefs = new UserPreference();
                    prefs.setUserId(userId);
                    return prefs;
                });

        existingPrefs.setDefaultAccountId(updatedPrefs.getDefaultAccountId());
        existingPrefs.setDefaultTransactionType(updatedPrefs.getDefaultTransactionType());
        existingPrefs.setDefaultCategoryId(updatedPrefs.getDefaultCategoryId());
        existingPrefs.setDefaultLabelId(updatedPrefs.getDefaultLabelId());

        return userPreferenceRepository.save(existingPrefs);
    }
}
