/**
 * NOTE: Use 'make test-int' to run integration tests and not gradle.
 */
package com.budget.tracker.integration;

import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.model.BackupRecord;
import com.budget.tracker.security.UserDetailsImpl;
import com.budget.tracker.service.AccountService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
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
    private AccountService accountService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userId;
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userDetails = new UserDetailsImpl(userId, "integration@test.com", "password");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testFullBackupAndRestoreFlow() throws Exception {
        // 1. Create some data
        Account account = new Account();
        account.setName("Integration Savings");
        account.setType(AccountType.BANK);
        account.setInitialBalance(new BigDecimal("5000"));
        
        mockMvc.perform(post("/api/v1/accounts")
                .with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(account)))
                .andExpect(status().isOk());

        // Verify data exists
        MvcResult listResultBefore = mockMvc.perform(get("/api/v1/accounts")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(listResultBefore.getResponse().getContentAsString().contains("Integration Savings"));

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
        String sqlContent = new String(backupContent);
        assertTrue(sqlContent.contains("Integration Savings"));

        // 4. Delete the account (simulate data loss or cleanup)
        MvcResult accountsRes = mockMvc.perform(get("/api/v1/accounts")
                .with(user(userDetails)))
                .andReturn();
        JsonNode accountsNode = objectMapper.readTree(accountsRes.getResponse().getContentAsString());
        String accountId = accountsNode.get(0).get("id").asText();

        mockMvc.perform(delete("/api/v1/accounts/" + accountId)
                .with(user(userDetails)))
                .andExpect(status().isNoContent());

        // Verify data is gone
        MvcResult listResultAfterDelete = mockMvc.perform(get("/api/v1/accounts")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andReturn();
        assertFalse(listResultAfterDelete.getResponse().getContentAsString().contains("Integration Savings"));

        // 5. Restore from backup
        MockMultipartFile file = new MockMultipartFile("file", "backup.sql", "text/plain", backupContent);
        mockMvc.perform(multipart("/api/v1/backups/import")
                .file(file)
                .with(user(userDetails)))
                .andExpect(status().isOk());

        // 6. Verify data is restored
        MvcResult listResultRestored = mockMvc.perform(get("/api/v1/accounts")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(listResultRestored.getResponse().getContentAsString().contains("Integration Savings"));
    }
}
