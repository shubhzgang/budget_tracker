package com.budget.tracker.util;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.model.Category;
import com.budget.tracker.model.Transaction;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.model.User;
import com.budget.tracker.repository.AccountRepository;
import com.budget.tracker.repository.CategoryRepository;
import com.budget.tracker.repository.UserRepository;
import com.budget.tracker.service.CategoryService;
import com.budget.tracker.service.LabelService;
import com.budget.tracker.service.TransactionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@Profile("demo")
@ConditionalOnProperty(name = "app.data-seeder.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private final TransactionService transactionService;

    public DataSeeder(UserRepository userRepository,
                      AccountRepository accountRepository,
                      CategoryRepository categoryRepository,
                      PasswordEncoder passwordEncoder,
                      CategoryService categoryService,
                      LabelService labelService,
                      TransactionService transactionService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryService = categoryService;
        this.labelService = labelService;
        this.transactionService = transactionService;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        String testEmail = "test@example.com";

        if (userRepository.existsByEmail(testEmail)) {
            System.out.println("Demo user already exists, skipping seeding.");
            return;
        }

        System.out.println("Seeding demo data...");

        // 1. Create User
        User user = new User();
        user.setEmail(testEmail);
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setCreatedAt(OffsetDateTime.now());
        User savedUser = userRepository.save(user);
        UUID userId = savedUser.getId();

        // Set AuthContext for services to work
        AuthContext.setUserId(userId);

        try {
            // 2. Initialize Defaults
            categoryService.initializeDefaultCategories(userId);
            labelService.initializeDefaultLabels(userId);

            // 3. Create Accounts
            Account mainBank = createAccount(userId, "Main Bank", AccountType.BANK, new BigDecimal("2500.00"), null);
            Account cash = createAccount(userId, "Cash", AccountType.CASH, new BigDecimal("120.50"), null);
            createAccount(userId, "Visa Credit", AccountType.CREDIT_CARD, new BigDecimal("450.00"), new BigDecimal("5000.00"));
            createAccount(userId, "Bob (Lend)", AccountType.FRIEND_LENDING, new BigDecimal("50.00"), null);

            // 4. Create Transactions

            // Find a category for Food
            Category foodCategory = categoryRepository.findAllByUserId(userId).stream()
                    .filter(c -> c.getName().equals("Food"))
                    .findFirst()
                    .orElse(null);

            if (foodCategory != null) {
                Transaction foodExpense = new Transaction();
                foodExpense.setAmount(new BigDecimal("25.00"));
                foodExpense.setType(TransactionType.EXPENSE);
                foodExpense.setDescription("Lunch");
                foodExpense.setTransactionDate(OffsetDateTime.now());
                foodExpense.setAccount(mainBank);
                foodExpense.setCategory(foodCategory);
                transactionService.createTransaction(foodExpense);
            }

            // Transfer from Main Bank to Cash
            Transaction transfer = new Transaction();
            transfer.setAccount(mainBank);
            transfer.setAmount(new BigDecimal("50.00"));
            transfer.setType(TransactionType.TRANSFER);
            transfer.setDescription("ATM Withdrawal");
            transfer.setTransactionDate(OffsetDateTime.now());
            
            transactionService.createTransfer(transfer, cash);

            System.out.println("Demo data seeded successfully.");
        } finally {
            AuthContext.clear();
        }
    }

    private Account createAccount(UUID userId, String name, AccountType type, BigDecimal balance, BigDecimal creditLimit) {
        Account account = new Account();
        account.setUserId(userId);
        account.setName(name);
        account.setType(type);
        account.setBalance(balance);
        account.setInitialBalance(balance);
        account.setCreditLimit(creditLimit);
        account.setCreatedAt(OffsetDateTime.now());
        return accountRepository.save(account);
    }
}
