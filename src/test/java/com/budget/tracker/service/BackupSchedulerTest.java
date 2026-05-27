package com.budget.tracker.service;

import com.budget.tracker.model.BackupRecord;
import com.budget.tracker.model.UserPreference;
import com.budget.tracker.repository.BackupRecordRepository;
import com.budget.tracker.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class BackupSchedulerTest {

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @Mock
    private BackupRecordRepository backupRecordRepository;

    @Mock
    private BackupService backupService;

    private BackupScheduler backupScheduler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        backupScheduler = new BackupScheduler(userPreferenceRepository, backupRecordRepository, backupService);
    }

    @Test
    void runAutoBackups_shouldNotBackup_whenDisabled() {
        // Arrange
        UserPreference pref = new UserPreference();
        pref.setUserId(UUID.randomUUID());
        pref.setAutoBackupEnabled(false);

        when(userPreferenceRepository.findAll()).thenReturn(List.of(pref));

        // Act
        backupScheduler.runAutoBackups();

        // Assert
        verifyNoInteractions(backupRecordRepository);
        verifyNoInteractions(backupService);
    }

    @Test
    void runAutoBackups_shouldBackup_whenEnabledAndNoPriorBackups() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserPreference pref = new UserPreference();
        pref.setUserId(userId);
        pref.setAutoBackupEnabled(true);
        pref.setAutoBackupFrequency("DAILY");
        pref.setAutoBackupFormat("SQL");

        when(userPreferenceRepository.findAll()).thenReturn(List.of(pref));
        when(backupRecordRepository.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(new ArrayList<>());

        // Act
        backupScheduler.runAutoBackups();

        // Assert
        verify(backupService).exportToSql(userId);
        verify(backupService, never()).exportToCsv(userId);
    }

    @Test
    void runAutoBackups_shouldNotBackup_whenLastBackupIsRecent() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserPreference pref = new UserPreference();
        pref.setUserId(userId);
        pref.setAutoBackupEnabled(true);
        pref.setAutoBackupFrequency("DAILY");
        pref.setAutoBackupFormat("CSV");

        BackupRecord recentRecord = new BackupRecord();
        recentRecord.setCreatedAt(OffsetDateTime.now());

        when(userPreferenceRepository.findAll()).thenReturn(List.of(pref));
        when(backupRecordRepository.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(recentRecord));

        // Act
        backupScheduler.runAutoBackups();

        // Assert
        verify(backupService, never()).exportToCsv(userId);
        verify(backupService, never()).exportToSql(userId);
    }

    @Test
    void runAutoBackups_shouldBackup_whenLastBackupIsOlderThanFrequency() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        UserPreference pref = new UserPreference();
        pref.setUserId(userId);
        pref.setAutoBackupEnabled(true);
        pref.setAutoBackupFrequency("WEEKLY");
        pref.setAutoBackupFormat("BOTH");

        BackupRecord oldRecord = new BackupRecord();
        oldRecord.setCreatedAt(OffsetDateTime.now().minusWeeks(2));

        when(userPreferenceRepository.findAll()).thenReturn(List.of(pref));
        when(backupRecordRepository.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(oldRecord));

        // Act
        backupScheduler.runAutoBackups();

        // Assert
        verify(backupService).exportToSql(userId);
        verify(backupService).exportToCsv(userId);
    }
}
