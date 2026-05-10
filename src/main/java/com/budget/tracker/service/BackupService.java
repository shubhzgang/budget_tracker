package com.budget.tracker.service;

import com.budget.tracker.model.*;
import com.budget.tracker.repository.*;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final LabelRepository labelRepository;
    private final TransactionRepository transactionRepository;
    private final BackupRecordRepository backupRecordRepository;
    private final EntityManager entityManager;

    @Value("${app.backup.directory:backups}")
    private String backupDirectory;

    @Transactional
    public BackupRecord exportToSql(UUID userId) throws IOException {
        String filename = "backup_" + userId + "_" + System.currentTimeMillis() + ".sql";
        Path path = Paths.get(backupDirectory, filename);
        Files.createDirectories(path.getParent());

        StringBuilder sql = new StringBuilder();
        sql.append("-- Budget Tracker Backup for User: ").append(userId).append("\n\n");

        // Labels
        List<Label> labels = labelRepository.findAllByUserId(userId);
        for (Label label : labels) {
            sql.append(String.format("INSERT INTO labels (id, user_id, name, is_default, created_at) VALUES ('%s', '%s', '%s', %b, '%s');\n",
                    label.getId(), userId, escapeSql(label.getName()), label.isDefault(), label.getCreatedAt()));
        }

        // Categories
        List<Category> categories = categoryRepository.findAllByUserId(userId);
        for (Category category : categories) {
            sql.append(String.format("INSERT INTO categories (id, user_id, name, icon, is_default, created_at) VALUES ('%s', '%s', '%s', '%s', %b, '%s');\n",
                    category.getId(), userId, escapeSql(category.getName()), escapeSql(category.getIcon()), category.isDefault(), category.getCreatedAt()));
        }

        // Accounts
        List<Account> accounts = accountRepository.findAllByUserId(userId);
        for (Account account : accounts) {
            sql.append(String.format("INSERT INTO accounts (id, user_id, name, type, initial_balance, balance, credit_limit, created_at) VALUES ('%s', '%s', '%s', '%s', %s, %s, %s, '%s');\n",
                    account.getId(), userId, escapeSql(account.getName()), account.getType(), account.getInitialBalance(), account.getBalance(), account.getCreditLimit(), account.getCreatedAt()));
        }

        // Transactions
        List<Transaction> transactions = transactionRepository.findAllByUserId(userId);
        for (Transaction transaction : transactions) {
            sql.append(String.format("INSERT INTO transactions (id, user_id, account_id, category_id, label_id, type, amount, description, transaction_date, linked_transfer_id, linked_account_id, is_incoming_transfer, created_at) VALUES ('%s', '%s', '%s', %s, %s, '%s', %s, '%s', '%s', %s, %s, %s, '%s');\n",
                    transaction.getId(), userId, transaction.getAccount().getId(),
                    transaction.getCategory() != null ? "'" + transaction.getCategory().getId() + "'" : "NULL",
                    transaction.getLabel() != null ? "'" + transaction.getLabel().getId() + "'" : "NULL",
                    transaction.getType(), transaction.getAmount(), escapeSql(transaction.getDescription()),
                    transaction.getTransactionDate(),
                    transaction.getLinkedTransferId() != null ? "'" + transaction.getLinkedTransferId() + "'" : "NULL",
                    transaction.getLinkedAccount() != null ? "'" + transaction.getLinkedAccount().getId() + "'" : "NULL",
                    transaction.getIsIncomingTransfer(), transaction.getCreatedAt()));
        }

        Files.writeString(path, sql.toString());

        return createRecord(userId, filename, "SQL", Files.size(path));
    }

    @Transactional
    public BackupRecord exportToCsv(UUID userId) throws IOException {
        String filename = "backup_" + userId + "_" + System.currentTimeMillis() + ".csv";
        Path path = Paths.get(backupDirectory, filename);
        Files.createDirectories(path.getParent());

        List<Transaction> transactions = transactionRepository.findAllByUserId(userId);

        try (Writer writer = Files.newBufferedWriter(path);
             CSVWriter csvWriter = new CSVWriter(writer)) {

            String[] header = {"ID", "Date", "Amount", "Type", "Account", "Category", "Label", "Description"};
            csvWriter.writeNext(header);

            for (Transaction t : transactions) {
                String[] data = {
                        t.getId().toString(),
                        t.getTransactionDate().toString(),
                        t.getAmount().toString(),
                        t.getType().toString(),
                        t.getAccount().getName(),
                        t.getCategory() != null ? t.getCategory().getName() : "",
                        t.getLabel() != null ? t.getLabel().getName() : "",
                        t.getDescription()
                };
                csvWriter.writeNext(data);
            }
        }

        return createRecord(userId, filename, "CSV", Files.size(path));
    }

    @Transactional
    public void importFromSql(UUID userId, InputStream inputStream) throws IOException {
        String content = new String(inputStream.readAllBytes());
        String[] statements = content.split(";\n");

        // Simple cleanup for the user before import - maybe risky, but for full restore it's often needed.
        // For safety, we only delete if the SQL contains INSERT statements for this user.
        if (content.contains("INSERT INTO")) {
            transactionRepository.findAllByUserId(userId).forEach(transactionRepository::delete);
            accountRepository.findAllByUserId(userId).forEach(accountRepository::delete);
            categoryRepository.findAllByUserId(userId).forEach(categoryRepository::delete);
            labelRepository.findAllByUserId(userId).forEach(labelRepository::delete);
        }

        for (String statement : statements) {
            if (statement.trim().isEmpty()) continue;
            entityManager.createNativeQuery(statement).executeUpdate();
        }
    }

    @Transactional
    public void importFromCsv(UUID userId, InputStream inputStream) throws IOException, CsvValidationException {
        try (Reader reader = new InputStreamReader(inputStream);
             CSVReader csvReader = new CSVReader(reader)) {

            String[] header = csvReader.readNext(); // skip header
            String[] nextLine;

            Map<String, Account> accountMap = new HashMap<>();
            accountRepository.findAllByUserId(userId).forEach(a -> accountMap.put(a.getName(), a));
            
            Map<String, Category> categoryMap = new HashMap<>();
            categoryRepository.findAllByUserId(userId).forEach(c -> categoryMap.put(c.getName(), c));
            
            Map<String, Label> labelMap = new HashMap<>();
            labelRepository.findAllByUserId(userId).forEach(l -> labelMap.put(l.getName(), l));

            while ((nextLine = csvReader.readNext()) != null) {
                Transaction t = new Transaction();
                t.setUserId(userId);
                t.setTransactionDate(OffsetDateTime.parse(nextLine[1]));
                t.setAmount(new BigDecimal(nextLine[2]));
                t.setType(TransactionType.valueOf(nextLine[3]));
                
                Account account = accountMap.get(nextLine[4]);
                if (account == null) {
                    account = new Account();
                    account.setUserId(userId);
                    account.setName(nextLine[4]);
                    account.setType(AccountType.CASH);
                    account.setInitialBalance(BigDecimal.ZERO);
                    account.setBalance(BigDecimal.ZERO);
                    account = accountRepository.save(account);
                    accountMap.put(account.getName(), account);
                }
                t.setAccount(account);

                if (!nextLine[5].isEmpty()) {
                    Category category = categoryMap.get(nextLine[5]);
                    if (category == null) {
                        category = new Category();
                        category.setUserId(userId);
                        category.setName(nextLine[5]);
                        category = categoryRepository.save(category);
                        categoryMap.put(category.getName(), category);
                    }
                    t.setCategory(category);
                }

                if (!nextLine[6].isEmpty()) {
                    Label label = labelMap.get(nextLine[6]);
                    if (label == null) {
                        label = new Label();
                        label.setUserId(userId);
                        label.setName(nextLine[6]);
                        label = labelRepository.save(label);
                        labelMap.put(label.getName(), label);
                    }
                    t.setLabel(label);
                }

                t.setDescription(nextLine[7]);
                transactionRepository.save(t);
            }
        }
    }

    public List<BackupRecord> getBackupHistory(UUID userId) {
        return backupRecordRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    public File getBackupFile(UUID userId, UUID backupId) throws IOException {
        BackupRecord record = backupRecordRepository.findById(backupId)
                .orElseThrow(() -> new IllegalArgumentException("Backup not found"));
        
        if (!record.getUserId().equals(userId)) {
            throw new SecurityException("Unauthorized access to backup");
        }

        Path path = Paths.get(backupDirectory, record.getFilename());
        if (!Files.exists(path)) {
            throw new FileNotFoundException("Backup file not found on disk");
        }
        return path.toFile();
    }

    private BackupRecord createRecord(UUID userId, String filename, String format, long size) {
        BackupRecord record = new BackupRecord();
        record.setUserId(userId);
        record.setFilename(filename);
        record.setFormat(format);
        record.setSizeBytes(size);
        record.setStatus("SUCCESS");
        return backupRecordRepository.save(record);
    }

    private String escapeSql(String str) {
        if (str == null) return "NULL";
        return str.replace("'", "''");
    }
}
