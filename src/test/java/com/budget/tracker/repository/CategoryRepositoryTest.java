package com.budget.tracker.repository;

import com.budget.tracker.model.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void shouldSaveAndFindCategory() {
        Category category = new Category();
        category.setName("Food");
        category.setIcon("food-icon");
        category.setDefault(true);
        category.setUserId(UUID.randomUUID());

        Category savedCategory = categoryRepository.save(category);
        java.util.Optional<Category> foundCategory = categoryRepository.findById(savedCategory.getId());

        assertThat(foundCategory).isPresent();
        assertThat(foundCategory.get().getName()).isEqualTo("Food");
        assertThat(foundCategory.get().getIcon()).isEqualTo("food-icon");
        assertThat(foundCategory.get().isDefault()).isTrue();
    }

    @Test
    void shouldFindCategoriesByUserId() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        Category category1 = new Category();
        category1.setName("Category 1");
        category1.setIcon("icon1");
        category1.setDefault(false);
        category1.setUserId(userId1);

        Category category2 = new Category();
        category2.setName("Category 2");
        category2.setIcon("icon2");
        category2.setDefault(false);
        category2.setUserId(userId2);

        categoryRepository.save(category1);
        categoryRepository.save(category2);

        List<Category> user1Categories = categoryRepository.findAllByUserId(userId1);
        List<Category> user2Categories = categoryRepository.findAllByUserId(userId2);

        assertThat(user1Categories).hasSize(1);
        assertThat(user1Categories.getFirst().getName()).isEqualTo("Category 1");
        assertThat(user2Categories).hasSize(1);
        assertThat(user2Categories.getFirst().getName()).isEqualTo("Category 2");
    }

    @Test
    void shouldNotFindCategoryByUserIdIfOwnedByAnotherUser() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        Category category1 = new Category();
        category1.setName("Category 1");
        category1.setIcon("icon1");
        category1.setDefault(false);
        category1.setUserId(userId1);
        categoryRepository.save(category1);

        List<Category> user2Categories = categoryRepository.findAllByUserId(userId2);

        assertThat(user2Categories).isEmpty();
    }
}
