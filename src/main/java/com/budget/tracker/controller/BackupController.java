package com.budget.tracker.controller;

import com.budget.tracker.model.BackupRecord;
import com.budget.tracker.service.BackupService;
import com.budget.tracker.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    @GetMapping
    public ResponseEntity<List<BackupRecord>> getBackupHistory() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(backupService.getBackupHistory(userId));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadBackup(@PathVariable UUID id) throws IOException {
        UUID userId = SecurityUtils.getCurrentUserId();
        File file = backupService.getBackupFile(userId, id);
        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .body(resource);
    }

    @PostMapping("/export")
    public ResponseEntity<BackupRecord> manualExport(@RequestParam String format) throws IOException {
        UUID userId = SecurityUtils.getCurrentUserId();
        BackupRecord record;
        if ("CSV".equalsIgnoreCase(format)) {
            record = backupService.exportToCsv(userId);
        } else {
            record = backupService.exportToSql(userId);
        }
        return ResponseEntity.ok(record);
    }

    @PostMapping("/import")
    public ResponseEntity<String> importBackup(@RequestParam("file") MultipartFile file) throws Exception {
        UUID userId = SecurityUtils.getCurrentUserId();
        String originalFilename = file.getOriginalFilename();

        if (originalFilename != null && originalFilename.endsWith(".csv")) {
            backupService.importFromCsv(userId, file.getInputStream());
        } else if (originalFilename != null && originalFilename.endsWith(".sql")) {
            backupService.importFromSql(userId, file.getInputStream());
        } else {
            return ResponseEntity.badRequest().body("Unsupported file format. Please upload .csv or .sql");
        }

        return ResponseEntity.ok("Backup imported successfully");
    }
}
