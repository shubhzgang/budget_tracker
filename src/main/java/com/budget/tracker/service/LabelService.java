package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Label;
import com.budget.tracker.repository.LabelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class LabelService {

    private final LabelRepository labelRepository;

    public LabelService(LabelRepository labelRepository) {
        this.labelRepository = labelRepository;
    }

    private UUID getCurrentUserId() {
        UUID userId = AuthContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("No authenticated user found in context");
        }
        return userId;
    }

    public Label createLabel(Label label) {
        label.setUserId(getCurrentUserId());
        return labelRepository.save(label);
    }

    public Label getLabelById(UUID labelId) {
        return labelRepository.findAllByUserId(getCurrentUserId()).stream()
                .filter(l -> l.getId().equals(labelId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Label not found or access denied"));
    }

    public List<Label> getAllLabelsForUser() {
        return labelRepository.findAllByUserId(getCurrentUserId());
    }

    public Label updateLabel(UUID labelId, Label labelDetails) {
        Label label = getLabelById(labelId);
        label.setName(labelDetails.getName());
        return labelRepository.save(label);
    }

    public void deleteLabel(UUID labelId) {
        Label label = getLabelById(labelId);
        labelRepository.delete(label);
    }

    public void initializeDefaultLabels(UUID userId) {
        long existingCount = labelRepository.findAllByUserId(userId).size();
        if (existingCount > 0) {
            return;
        }

        String[] defaultNames = {"NEEDS", "WANTS", "SAVINGS"};
        for (String name : defaultNames) {
            Label label = new Label();
            label.setName(name);
            label.setDefault(true);
            label.setUserId(userId);
            labelRepository.save(label);
        }
    }
}
