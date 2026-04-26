package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
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

    private UUID getCurrentUserId() {
        UUID userId = AuthContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("No authenticated user found in context");
        }
        return userId;
    }

    public Category createCategory(Category category) {
        category.setUserId(getCurrentUserId());
        return categoryRepository.save(category);
    }

    public Category getCategoryById(UUID categoryId) {
        return categoryRepository.findAllByUserId(getCurrentUserId()).stream()
                .filter(c -> c.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Category not found or access denied"));
    }

    public List<Category> getAllCategoriesForUser() {
        return categoryRepository.findAllByUserId(getCurrentUserId());
    }

    public Category updateCategory(UUID categoryId, Category categoryDetails) {
        Category category = getCategoryById(categoryId);
        category.setName(categoryDetails.getName());
        category.setIcon(categoryDetails.getIcon());
        return categoryRepository.save(category);
    }

    public void deleteCategory(UUID categoryId) {
        Category category = getCategoryById(categoryId);
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
