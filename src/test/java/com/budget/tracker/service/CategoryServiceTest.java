package com.budget.tracker.service;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Category;
import com.budget.tracker.repository.CategoryRepository;
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

class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    private UUID userId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        categoryService = new CategoryService(categoryRepository);
        userId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        AuthContext.setUserId(userId);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void createCategory_shouldSaveCategory() {
        Category category = new Category();
        category.setName("Food");

        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category savedCategory = categoryService.createCategory(category);

        assertNotNull(savedCategory);
        assertEquals("Food", savedCategory.getName());
        assertEquals(userId, savedCategory.getUserId());
        verify(categoryRepository, times(1)).save(category);
    }

    @Test
    void getCategoryById_shouldReturnCategory_whenExists() {
        Category category = new Category();
        category.setId(categoryId);
        category.setUserId(userId);

        when(categoryRepository.findAllByUserId(userId)).thenReturn(List.of(category));

        Category foundCategory = categoryService.getCategoryById(categoryId);

        assertNotNull(foundCategory);
        assertEquals(categoryId, foundCategory.getId());
    }

    @Test
    void getCategoryById_shouldThrowException_whenNotFound() {
        when(categoryRepository.findAllByUserId(userId)).thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> categoryService.getCategoryById(categoryId));
    }

    @Test
    void getAllCategoriesForUser_shouldReturnAllCategories() {
        Category cat1 = new Category();
        cat1.setId(UUID.randomUUID());
        cat1.setUserId(userId);
        Category cat2 = new Category();
        cat2.setId(UUID.randomUUID());
        cat2.setUserId(userId);

        when(categoryRepository.findAllByUserId(userId)).thenReturn(List.of(cat1, cat2));

        List<Category> categories = categoryService.getAllCategoriesForUser();

        assertEquals(2, categories.size());
    }

    @Test
    void updateCategory_shouldUpdateAndSaveCategory() {
        Category existing = new Category();
        existing.setId(categoryId);
        existing.setUserId(userId);
        existing.setName("Old Name");

        Category details = new Category();
        details.setName("New Name");
        details.setIcon("🍕");

        when(categoryRepository.findAllByUserId(userId)).thenReturn(List.of(existing));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category updated = categoryService.updateCategory(categoryId, details);

        assertEquals("New Name", updated.getName());
        assertEquals("🍕", updated.getIcon());
        verify(categoryRepository, times(1)).save(existing);
    }

    @Test
    void deleteCategory_shouldDeleteCategory() {
        Category category = new Category();
        category.setId(categoryId);
        category.setUserId(userId);

        when(categoryRepository.findAllByUserId(userId)).thenReturn(List.of(category));

        categoryService.deleteCategory(categoryId);

        verify(categoryRepository, times(1)).delete(category);
    }

    @Test
    void initializeDefaultCategories_shouldCreateDefaults_whenNoneExist() {
        when(categoryRepository.findAllByUserId(userId)).thenReturn(List.of());

        categoryService.initializeDefaultCategories(userId);

        verify(categoryRepository, times(3)).save(any(Category.class));

        verify(categoryRepository).save(argThat(cat ->
                cat.getName().equals("Food") && cat.isDefault() && cat.getUserId().equals(userId)
        ));
    }
}
