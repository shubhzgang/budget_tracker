package com.budget.tracker.controller;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.BackupRecord;
import com.budget.tracker.security.UserDetailsImpl;
import com.budget.tracker.service.BackupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BackupService backupService;

    private UUID userId;
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userDetails = new UserDetailsImpl(userId, "test@test.com", "password");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        AuthContext.setUserId(userId);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void getBackupHistory_shouldReturnList() throws Exception {
        BackupRecord record = new BackupRecord();
        record.setId(UUID.randomUUID());
        record.setFilename("test.sql");

        when(backupService.getBackupHistory(userId)).thenReturn(List.of(record));

        mockMvc.perform(get("/api/v1/backups")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("test.sql"));
    }

    @Test
    void manualExport_shouldTriggerExport() throws Exception {
        BackupRecord record = new BackupRecord();
        record.setFormat("CSV");

        when(backupService.exportToCsv(userId)).thenReturn(record);

        mockMvc.perform(post("/api/v1/backups/export")
                .param("format", "CSV")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("CSV"));
        
        verify(backupService).exportToCsv(userId);
    }

    @Test
    void importBackup_shouldProcessFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/backups/import")
                .file(file)
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string("Backup imported successfully"));

        verify(backupService).importFromCsv(eq(userId), any(InputStream.class));
    }

    @Test
    void downloadBackup_shouldReturnFileContent() throws Exception {
        UUID backupId = UUID.randomUUID();
        File tempFile = File.createTempFile("backup", ".sql");
        tempFile.deleteOnExit();

        when(backupService.getBackupFile(userId, backupId)).thenReturn(tempFile);

        mockMvc.perform(get("/api/v1/backups/" + backupId + "/download")
                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + tempFile.getName() + "\""))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }
}
