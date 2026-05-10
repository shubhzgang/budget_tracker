package com.budget.tracker.service;

import com.budget.tracker.model.*;
import com.budget.tracker.repository.*;
import com.opencsv.exceptions.CsvValidationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BackupServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private LabelRepository labelRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private BackupRecordRepository backupRecordRepository;
    @Mock
    private EntityManager entityManager;

    private BackupService backupService;
    private UUID userId;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        backupService = new BackupService(
                accountRepository,
                categoryRepository,
                labelRepository,
                transactionRepository,
                backupRecordRepository,
                entityManager
        );
        userId = UUID.randomUUID();
        ReflectionTestUtils.setField(backupService, "backupDirectory", tempDir.toString());
    }

    @Test
    void exportToSql_shouldGenerateFileAndRecord() throws IOException {
        // Arrange
        Label label = new Label();
        label.setId(UUID.randomUUID());
        label.setName("Test Label");
        label.setCreatedAt(OffsetDateTime.now());
        when(labelRepository.findAllByUserId(userId)).thenReturn(List.of(label));

        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Test Category");
        category.setCreatedAt(OffsetDateTime.now());
        when(categoryRepository.findAllByUserId(userId)).thenReturn(List.of(category));

        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setName("Test Account");
        account.setType(AccountType.BANK);
        account.setInitialBalance(BigDecimal.ZERO);
        account.setBalance(BigDecimal.ZERO);
        account.setCreatedAt(OffsetDateTime.now());
        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(account));

        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setAccount(account);
        transaction.setType(TransactionType.EXPENSE);
        transaction.setAmount(new BigDecimal("100"));
        transaction.setDescription("Test Transaction");
        transaction.setTransactionDate(OffsetDateTime.now());
        transaction.setCreatedAt(OffsetDateTime.now());
        when(transactionRepository.findAllByUserId(userId)).thenReturn(List.of(transaction));

        when(backupRecordRepository.save(any(BackupRecord.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        BackupRecord result = backupService.exportToSql(userId);

        // Assert
        assertNotNull(result);
        assertEquals("SQL", result.getFormat());
        assertTrue(new File(tempDir.toFile(), result.getFilename()).exists());
        verify(backupRecordRepository).save(any(BackupRecord.class));
    }

    @Test
    void exportToCsv_shouldGenerateFileAndRecord() throws IOException {
        // Arrange
        Account account = new Account();
        account.setName("Test Account");

        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setAccount(account);
        transaction.setType(TransactionType.EXPENSE);
        transaction.setAmount(new BigDecimal("100"));
        transaction.setDescription("Test CSV");
        transaction.setTransactionDate(OffsetDateTime.now());
        
        when(transactionRepository.findAllByUserId(userId)).thenReturn(List.of(transaction));
        when(backupRecordRepository.save(any(BackupRecord.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        BackupRecord result = backupService.exportToCsv(userId);

        // Assert
        assertNotNull(result);
        assertEquals("CSV", result.getFormat());
        assertTrue(new File(tempDir.toFile(), result.getFilename()).exists());
    }

    @Test
    void importFromSql_shouldExecuteStatements() throws IOException {
        // Arrange
        String sql = "INSERT INTO labels ...;\nINSERT INTO accounts ...;";
        ByteArrayInputStream bais = new ByteArrayInputStream(sql.getBytes());
        Query mockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);

        // Act
        backupService.importFromSql(userId, bais);

        // Assert
        verify(entityManager, atLeastOnce()).createNativeQuery(anyString());
    }

    @Test
    void importFromCsv_shouldCreateTransactions() throws IOException, CsvValidationException {
        // Arrange
        String csv = "ID,Date,Amount,Type,Account,Category,Label,Description\n" +
                     UUID.randomUUID() + "," + OffsetDateTime.now() + ",150.00,EXPENSE,Cash,Food,Personal,Groceries";
        ByteArrayInputStream bais = new ByteArrayInputStream(csv.getBytes());

        Account account = new Account();
        account.setName("Cash");
        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(account));
        when(categoryRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(labelRepository.findAllByUserId(userId)).thenReturn(List.of());
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));
        when(labelRepository.save(any(Label.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        backupService.importFromCsv(userId, bais);

        // Assert
        verify(transactionRepository, atLeastOnce()).save(any(Transaction.class));
    }

    @Test
    void getBackupFile_shouldReturnFile_whenAuthorized() throws IOException {
        // Arrange
        UUID backupId = UUID.randomUUID();
        BackupRecord record = new BackupRecord();
        record.setId(backupId);
        record.setUserId(userId);
        record.setFilename("test_backup.sql");
        
        File file = new File(tempDir.toFile(), "test_backup.sql");
        file.createNewFile();

        when(backupRecordRepository.findById(backupId)).thenReturn(Optional.of(record));

        // Act
        File result = backupService.getBackupFile(userId, backupId);

        // Assert
        assertNotNull(result);
        assertEquals("test_backup.sql", result.getName());
    }

    @Test
    void getBackupFile_shouldThrow_whenUnauthorized() {
        // Arrange
        UUID backupId = UUID.randomUUID();
        BackupRecord record = new BackupRecord();
        record.setId(backupId);
        record.setUserId(UUID.randomUUID()); // Different user

        when(backupRecordRepository.findById(backupId)).thenReturn(Optional.of(record));

        // Act & Assert
        assertThrows(SecurityException.class, () -> backupService.getBackupFile(userId, backupId));
    }
}
