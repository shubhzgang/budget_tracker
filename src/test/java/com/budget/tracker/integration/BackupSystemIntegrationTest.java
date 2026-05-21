/**
 * NOTE: Use 'make test-int' to run integration tests and not gradle.
 */
package com.budget.tracker.integration;

import com.budget.tracker.model.BackupRecord;
import com.budget.tracker.repository.AccountRepository;
import com.budget.tracker.repository.CategoryRepository;
import com.budget.tracker.repository.UserRepository;
import com.budget.tracker.security.UserDetailsImpl;
import com.budget.tracker.service.CategoryService;
import com.budget.tracker.service.LabelService;
import com.budget.tracker.service.TransactionService;
import com.budget.tracker.service.TransferService;
import com.budget.tracker.service.UserPreferenceService;
import com.budget.tracker.util.DataSeeder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BackupSystemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private LabelService labelService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private UserPreferenceService userPreferenceService;

    private static final UUID DEMO_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String DEMO_EMAIL = "test@example.com";
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() throws Exception {
        // Ensure user doesn't exist to allow seeder to run
        if (userRepository.existsByEmail(DEMO_EMAIL)) {
            userRepository.deleteById(DEMO_USER_ID);
        }

        userDetails = new UserDetailsImpl(DEMO_USER_ID, DEMO_EMAIL, "password");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (userRepository.existsByEmail(DEMO_EMAIL)) {
            userRepository.deleteById(DEMO_USER_ID);
        }
    }

    @Test
    void testFullBackupAndRestoreFlow() throws Exception {
        // 1. Seed Data using DataSeeder
        DataSeeder seeder = new DataSeeder(
                userRepository,
                accountRepository,
                categoryRepository,
                passwordEncoder,
                categoryService,
                labelService,
                transactionService,
                transferService,
                userPreferenceService
        );
        seeder.run();

        // Verify data exists (Seeder creates 4 accounts and 2 transactions)
        MvcResult accountsBefore = mockMvc.perform(get("/api/v1/accounts")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(accountsBefore.getResponse().getContentAsString().contains("Main Bank"));
        assertTrue(accountsBefore.getResponse().getContentAsString().contains("Bob (Lend)"));

        MvcResult activityBefore = mockMvc.perform(get("/api/v1/activity")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(activityBefore.getResponse().getContentAsString().contains("Lunch"));
        assertTrue(activityBefore.getResponse().getContentAsString().contains("ATM Withdrawal"));

        // 2. Export to SQL
        MvcResult exportResult = mockMvc.perform(post("/api/v1/backups/export")
                .param("format", "SQL")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andReturn();
        
        BackupRecord record = objectMapper.readValue(exportResult.getResponse().getContentAsString(), BackupRecord.class);
        assertNotNull(record.getId());
        assertEquals("SQL", record.getFormat());

        // 3. Download the backup
        MvcResult downloadResult = mockMvc.perform(get("/api/v1/backups/" + record.getId() + "/download")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andReturn();
        
        byte[] backupContent = downloadResult.getResponse().getContentAsByteArray();
        assertTrue(backupContent.length > 0);
        String sqlContent = new String(backupContent, StandardCharsets.UTF_8);
        assertTrue(sqlContent.contains("Main Bank"));
        assertTrue(sqlContent.contains("Lunch"));
        assertTrue(sqlContent.contains("₹"));

        // 4. Delete All Data
        mockMvc.perform(delete("/api/v1/backups/clear")
                .with(user(userDetails)))
                .andExpect(status().isNoContent());

        // Verify data is gone
        mockMvc.perform(get("/api/v1/accounts")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("[]", result.getResponse().getContentAsString()));

        mockMvc.perform(get("/api/v1/activity")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("\"content\":[]")));

        // 5. Restore from backup
        MockMultipartFile file = new MockMultipartFile("file", "backup.sql", "text/plain", backupContent);
        mockMvc.perform(multipart("/api/v1/backups/import")
                .file(file)
                .with(user(userDetails)))
                .andExpect(status().isOk());

        // 6. Verify data is restored
        MvcResult accountsRestored = mockMvc.perform(get("/api/v1/accounts")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(accountsRestored.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("Main Bank"));
        assertTrue(accountsRestored.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("Bob (Lend)"));

        MvcResult activityRestored = mockMvc.perform(get("/api/v1/activity")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(activityRestored.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("Lunch"));
        assertTrue(activityRestored.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("ATM Withdrawal"));
        
        // Check preferences (DataSeeder sets currency to ₹)
        MvcResult prefsRestored = mockMvc.perform(get("/api/v1/preferences")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(prefsRestored.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("₹"));
    }
}
