package com.budget.tracker.service;

import com.budget.tracker.model.Category;
import com.budget.tracker.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Category createCategory(Category category) {
        return categoryRepository.save(category);
    }

    public Category getCategoryById(UUID categoryId, UUID userId) {
        return categoryRepository.findAllByUserId(userId).stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Category not found or access denied"));
    }

    public List<Category> getAllCategoriesForUser(UUID userId) {
        return categoryRepository.findAllByUserId(userId);
    }

    public Category updateCategory(UUID categoryId, UUID userId, Category categoryDetails) {
        Category category = getCategoryById(categoryId, userId);
        category.setName(categoryDetails.getName());
        category.setIcon(categoryDetails.getIcon());
        return categoryRepository.save(category);
    }

    public void deleteCategory(UUID categoryId, UUID userId) {
        Category category = getCategoryById(categoryId, userId);
        categoryRepository.delete(category);
    }

    public void initializeDefaultCategories(UUID userId) {
        long existingCount = categoryRepository.findAllByUserId(userId).size();
        if (existingCount > 0) {
            return;
        }

        String[] defaultNames = {"Food", "Travel", "Transfer"};
        String[] defaultIcons = {"🍔", "✈️", "🔄"};
        for (int i = 0; i < defaultNames.length; i++) {
            Category category = new Category();
            category.setName(defaultNames[i]);
            category.setIcon(defaultIcons[i]);
            category.setDefault(true);
            category.setUserId(userId);
            categoryRepository.save(category);
        }
    }
}