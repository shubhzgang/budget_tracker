package com.budget.tracker.controller;

import com.budget.tracker.model.Label;
import com.budget.tracker.service.LabelService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/labels")
public class LabelController {

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @GetMapping
    public List<Label> getAllLabels() {
        return labelService.getAllLabelsForUser();
    }

    @GetMapping("/{id}")
    public Label getLabelById(@PathVariable UUID id) {
        return labelService.getLabelById(id);
    }

    @PostMapping
    public Label createLabel(@Valid @RequestBody Label label) {
        return labelService.createLabel(label);
    }

    @PutMapping("/{id}")
    public Label updateLabel(@PathVariable UUID id, @Valid @RequestBody Label labelDetails) {
        return labelService.updateLabel(id, labelDetails);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLabel(@PathVariable UUID id) {
        labelService.deleteLabel(id);
        return ResponseEntity.noContent().build();
    }
}
