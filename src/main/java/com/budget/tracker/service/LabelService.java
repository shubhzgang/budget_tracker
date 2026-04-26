package com.budget.tracker.service;

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

    public Label createLabel(Label label) {
        return labelRepository.save(label);
    }

    public Label getLabelById(UUID labelId, UUID userId) {
        return labelRepository.findAllByUserId(userId).stream()
                .filter(l -> l.getId().equals(labelId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Label not found or access denied"));
    }

    public List<Label> getAllLabelsForUser(UUID userId) {
        return labelRepository.findAllByUserId(userId);
    }

    public Label updateLabel(UUID labelId, UUID userId, Label labelDetails) {
        Label label = getLabelById(labelId, userId);
        label.setName(labelDetails.getName());
        return labelRepository.save(label);
    }

    public void deleteLabel(UUID labelId, UUID userId) {
        Label label = getLabelById(labelId, userId);
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
