package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Label;
import com.budget.tracker.repository.LabelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LabelServiceTest {

    @Mock
    private LabelRepository labelRepository;

    private LabelService labelService;

    private UUID userId;
    private UUID labelId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        labelService = new LabelService(labelRepository);
        userId = UUID.randomUUID();
        labelId = UUID.randomUUID();
        AuthContext.setUserId(userId);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void createLabel_shouldSaveLabel() {
        Label label = new Label();
        label.setName("NEEDS");

        when(labelRepository.save(any(Label.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Label savedLabel = labelService.createLabel(label);

        assertNotNull(savedLabel);
        assertEquals("NEEDS", savedLabel.getName());
        assertEquals(userId, savedLabel.getUserId());
        verify(labelRepository, times(1)).save(label);
    }

    @Test
    void getLabelById_shouldReturnLabel_whenExists() {
        Label label = new Label();
        label.setId(labelId);
        label.setUserId(userId);

        when(labelRepository.findAllByUserId(userId)).thenReturn(List.of(label));

        Label foundLabel = labelService.getLabelById(labelId);

        assertNotNull(foundLabel);
        assertEquals(labelId, foundLabel.getId());
    }

    @Test
    void getLabelById_shouldThrowException_whenNotFound() {
        when(labelRepository.findAllByUserId(userId)).thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> labelService.getLabelById(labelId));
    }

    @Test
    void getAllLabelsForUser_shouldReturnAllLabels() {
        Label label1 = new Label();
        label1.setId(UUID.randomUUID());
        label1.setUserId(userId);
        Label label2 = new Label();
        label2.setId(UUID.randomUUID());
        label2.setUserId(userId);

        when(labelRepository.findAllByUserId(userId)).thenReturn(List.of(label1, label2));

        List<Label> labels = labelService.getAllLabelsForUser();

        assertEquals(2, labels.size());
    }

    @Test
    void updateLabel_shouldUpdateAndSaveLabel() {
        Label existing = new Label();
        existing.setId(labelId);
        existing.setUserId(userId);
        existing.setName("Old Label");

        Label details = new Label();
        details.setName("New Label");

        when(labelRepository.findAllByUserId(userId)).thenReturn(List.of(existing));
        when(labelRepository.save(any(Label.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Label updated = labelService.updateLabel(labelId, details);

        assertEquals("New Label", updated.getName());
        verify(labelRepository, times(1)).save(existing);
    }

    @Test
    void deleteLabel_shouldDeleteLabel() {
        Label label = new Label();
        label.setId(labelId);
        label.setUserId(userId);

        when(labelRepository.findAllByUserId(userId)).thenReturn(List.of(label));

        labelService.deleteLabel(labelId);

        verify(labelRepository, times(1)).delete(label);
    }

    @Test
    void initializeDefaultLabels_shouldCreateDefaults_whenNoneExist() {
        when(labelRepository.findAllByUserId(userId)).thenReturn(List.of());

        labelService.initializeDefaultLabels(userId);

        verify(labelRepository, times(3)).save(any(Label.class));

        verify(labelRepository).save(argThat(l ->
                l.getName().equals("NEEDS") && l.isDefault() && l.getUserId().equals(userId)
        ));
    }
}
