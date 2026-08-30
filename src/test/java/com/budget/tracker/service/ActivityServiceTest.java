package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.repository.ActivityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class ActivityServiceTest {

    @Mock
    private ActivityRepository activityRepository;

    private ActivityService activityService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        activityService = new ActivityService(activityRepository);
        userId = UUID.randomUUID();
        AuthContext.setUserId(userId);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void getActivityById_notFound_throwsRuntimeException() {
        UUID id = UUID.randomUUID();
        when(activityRepository.findByIdWithRelations(id, userId)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> activityService.getActivityById(id));
    }

    @Test
    void getActivityById_notFound_messageIndicatesMissingOrDenied() {
        UUID id = UUID.randomUUID();
        when(activityRepository.findByIdWithRelations(id, userId)).thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> activityService.getActivityById(id));
        assertTrue(ex.getMessage().toLowerCase().contains("not found"));
    }
}
