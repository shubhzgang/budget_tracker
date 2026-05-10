package com.budget.tracker.service;

import com.budget.tracker.model.BackupRecord;
import com.budget.tracker.model.UserPreference;
import com.budget.tracker.repository.BackupRecordRepository;
import com.budget.tracker.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BackupScheduler {

    private final UserPreferenceRepository userPreferenceRepository;
    private final BackupRecordRepository backupRecordRepository;
    private final BackupService backupService;

    // Run every day at 1 AM
    @Scheduled(cron = "0 0 1 * * ?")
    public void runAutoBackups() {
        log.info("Starting automatic backup process...");
        List<UserPreference> preferences = userPreferenceRepository.findAll();

        for (UserPreference pref : preferences) {
            if (Boolean.TRUE.equals(pref.getAutoBackupEnabled())) {
                try {
                    processAutoBackup(pref);
                } catch (Exception e) {
                    log.error("Failed to process auto backup for user: {}", pref.getUserId(), e);
                }
            }
        }
    }

    private void processAutoBackup(UserPreference pref) throws Exception {
        List<BackupRecord> lastBackups = backupRecordRepository.findAllByUserIdOrderByCreatedAtDesc(pref.getUserId());
        
        boolean shouldBackup = false;
        if (lastBackups.isEmpty()) {
            shouldBackup = true;
        } else {
            OffsetDateTime lastBackupDate = lastBackups.get(0).getCreatedAt();
            String frequency = pref.getAutoBackupFrequency();

            if ("DAILY".equalsIgnoreCase(frequency)) {
                shouldBackup = lastBackupDate.isBefore(OffsetDateTime.now().minusDays(1));
            } else if ("WEEKLY".equalsIgnoreCase(frequency)) {
                shouldBackup = lastBackupDate.isBefore(OffsetDateTime.now().minusWeeks(1));
            } else if ("MONTHLY".equalsIgnoreCase(frequency)) {
                shouldBackup = lastBackupDate.isBefore(OffsetDateTime.now().minusMonths(1));
            }
        }

        if (shouldBackup) {
            log.info("Triggering auto backup for user: {} (Format: {})", pref.getUserId(), pref.getAutoBackupFormat());
            String format = pref.getAutoBackupFormat();
            if ("SQL".equalsIgnoreCase(format)) {
                backupService.exportToSql(pref.getUserId());
            } else if ("CSV".equalsIgnoreCase(format)) {
                backupService.exportToCsv(pref.getUserId());
            } else {
                // Default to both or SQL if not specified
                backupService.exportToSql(pref.getUserId());
                backupService.exportToCsv(pref.getUserId());
            }
        }
    }
}
