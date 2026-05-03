package com.budget.tracker.service;

import com.budget.tracker.model.UserPreference;
import com.budget.tracker.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserPreferenceServiceTest {

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @InjectMocks
    private UserPreferenceService userPreferenceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getPreferences_ReturnsExisting_WhenFound() {
        UUID userId = UUID.randomUUID();
        UserPreference prefs = new UserPreference();
        prefs.setUserId(userId);
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(prefs));

        UserPreference result = userPreferenceService.getPreferences(userId);

        assertEquals(userId, result.getUserId());
        verify(userPreferenceRepository).findByUserId(userId);
    }

    @Test
    void getPreferences_ReturnsNew_WhenNotFound() {
        UUID userId = UUID.randomUUID();
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.empty());

        UserPreference result = userPreferenceService.getPreferences(userId);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
    }

    @Test
    void updatePreferences_SavesUpdated_WhenFound() {
        UUID userId = UUID.randomUUID();
        UserPreference existing = new UserPreference();
        existing.setUserId(userId);
        
        UserPreference updated = new UserPreference();
        UUID accountId = UUID.randomUUID();
        updated.setDefaultAccountId(accountId);

        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(userPreferenceRepository.save(any(UserPreference.class))).thenAnswer(i -> i.getArguments()[0]);

        UserPreference result = userPreferenceService.updatePreferences(userId, updated);

        assertEquals(accountId, result.getDefaultAccountId());
        verify(userPreferenceRepository).save(existing);
    }
}
