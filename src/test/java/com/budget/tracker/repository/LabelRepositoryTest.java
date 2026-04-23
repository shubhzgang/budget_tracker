package com.budget.tracker.repository;

import com.budget.tracker.model.Label;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class LabelRepositoryTest {

    @Autowired
    private LabelRepository labelRepository;

    @Test
    void shouldSaveAndFindLabel() {
        Label label = new Label();
        label.setName("Needs");
        label.setDefault(true);
        label.setUserId(UUID.randomUUID());

        Label savedLabel = labelRepository.save(label);
        java.util.Optional<Label> foundLabel = labelRepository.findById(savedLabel.getId());

        assertThat(foundLabel).isPresent();
        assertThat(foundLabel.get().getName()).isEqualTo("Needs");
        assertThat(foundLabel.get().isDefault()).isTrue();
    }

    @Test
    void shouldFindLabelsByUserId() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        Label label1 = new Label();
        label1.setName("Label 1");
        label1.setDefault(false);
        label1.setUserId(userId1);

        Label label2 = new Label();
        label2.setName("Label 2");
        label2.setDefault(false);
        label2.setUserId(userId2);

        labelRepository.save(label1);
        labelRepository.save(label2);

        List<Label> user1Labels = labelRepository.findAllByUserId(userId1);
        List<Label> user2Labels = labelRepository.findAllByUserId(userId2);

        assertThat(user1Labels).hasSize(1);
        assertThat(user1Labels.get(0).getName()).isEqualTo("Label 1");
        assertThat(user2Labels).hasSize(1);
        assertThat(user2Labels.get(0).getName()).isEqualTo("Label 2");
    }
}