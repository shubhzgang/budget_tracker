package com.budget.tracker.controller;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Category;
import com.budget.tracker.security.UserDetailsImpl;
import com.budget.tracker.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userId;
    private UUID categoryId;
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        // Mock Security Context
        userDetails = new UserDetailsImpl(userId, "test@test.com", "password");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Mock AuthContext
        AuthContext.setUserId(userId);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateCategory() throws Exception {
        Category category = new Category();
        category.setName("Food");
        category.setIcon("🍔");

        Category saved = new Category();
        saved.setId(categoryId);
        saved.setName("Food");
        saved.setIcon("🍔");
        saved.setUserId(userId);

        when(categoryService.createCategory(any(Category.class))).thenReturn(saved);

        mockMvc.perform(post("/api/v1/categories")
                .with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(category)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.name").value("Food"))
                .andExpect(jsonPath("$.icon").value("🍔"));
    }

    @Test
    void shouldGetAllCategories() throws Exception {
        Category category = new Category();
        category.setId(categoryId);
        category.setName("Travel");

        when(categoryService.getAllCategoriesForUser()).thenReturn(List.of(category));

        mockMvc.perform(get("/api/v1/categories")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(categoryId.toString()))
                .andExpect(jsonPath("$[0].name").value("Travel"));
    }

    @Test
    void shouldGetCategoryById() throws Exception {
        Category category = new Category();
        category.setId(categoryId);
        category.setName("Utilities");

        when(categoryService.getCategoryById(categoryId)).thenReturn(category);

        mockMvc.perform(get("/api/v1/categories/" + categoryId)
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.name").value("Utilities"));
    }

    @Test
    void shouldUpdateCategory() throws Exception {
        Category details = new Category();
        details.setName("Updated Food");
        details.setIcon("🍕");

        Category updated = new Category();
        updated.setId(categoryId);
        updated.setName("Updated Food");
        updated.setIcon("🍕");

        when(categoryService.updateCategory(eq(categoryId), any(Category.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/categories/" + categoryId)
                .with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(details)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Food"))
                .andExpect(jsonPath("$.icon").value("🍕"));
    }

    @Test
    void shouldDeleteCategory() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/" + categoryId)
                .with(user(userDetails)))
                .andExpect(status().isNoContent());
    }
}
