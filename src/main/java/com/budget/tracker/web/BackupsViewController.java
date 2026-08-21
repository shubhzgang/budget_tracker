package com.budget.tracker.web;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.service.BackupService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Controller
public class BackupsViewController {

    private final BackupService backupService;

    public BackupsViewController(BackupService backupService) {
        this.backupService = backupService;
    }

    @PostMapping("/backups/export")
    public String export(@RequestParam String format,
                         Model model,
                         HttpServletResponse response) {
        UUID userId = AuthContext.getUserId();
        boolean csv = "CSV".equalsIgnoreCase(format);
        try {
            if (csv) {
                backupService.exportToCsv(userId);
            } else {
                backupService.exportToSql(userId);
            }
            response.setHeader("HX-Trigger",
                    "{\"toast-success\":\"" + (csv ? "CSV" : "SQL") + " export triggered successfully\"}");
        } catch (IOException e) {
            response.setHeader("HX-Trigger", "{\"toast-error\":\"Failed to trigger export.\"}");
        }
        addBackupAttributes(model);
        return "fragments/backup-manager";
    }

    @PostMapping("/backups/import")
    public String importBackup(@RequestParam("file") MultipartFile file,
                               Model model,
                               HttpServletResponse response) {
        UUID userId = AuthContext.getUserId();
        String name = file.getOriginalFilename();
        String trigger;
        if (name != null && name.toLowerCase().endsWith(".csv")) {
            trigger = doImport(userId, file, true);
        } else if (name != null && name.toLowerCase().endsWith(".sql")) {
            trigger = doImport(userId, file, false);
        } else {
            trigger = "{\"toast-error\":\"Unsupported file format. Please upload .csv or .sql\"}";
        }
        response.setHeader("HX-Trigger", trigger);
        addBackupAttributes(model);
        return "fragments/backup-manager";
    }

    private String doImport(UUID userId, MultipartFile file, boolean csv) {
        try {
            if (csv) {
                backupService.importFromCsv(userId, file.getInputStream());
            } else {
                backupService.importFromSql(userId, file.getInputStream());
            }
            return "{\"toast-success\":\"Data restored successfully. Please refresh the page.\"}";
        } catch (Exception e) {
            return "{\"toast-error\":\"Failed to restore backup.\"}";
        }
    }

    @GetMapping("/backups/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id) throws IOException {
        UUID userId = AuthContext.getUserId();
        File file = backupService.getBackupFile(userId, id);
        Resource resource = new FileSystemResource(file);
        String encoded = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(encoded, StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }

    @DeleteMapping("/backups/clear")
    public String clearAll(Model model, HttpServletResponse response) {
        backupService.clearUserData(AuthContext.getUserId());
        response.setHeader("HX-Trigger", "{\"toast-success\":\"All data has been deleted.\"}");
        addBackupAttributes(model);
        return "fragments/backup-manager";
    }

    private void addBackupAttributes(Model model) {
        model.addAttribute("backups", backupService.getBackupHistory(AuthContext.getUserId()));
    }
}
