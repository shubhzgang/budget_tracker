package com.budget.tracker.util;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.model.Category;
import com.budget.tracker.model.Transaction;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.model.User;
import com.budget.tracker.model.UserPreference;
import com.budget.tracker.repository.AccountRepository;
import com.budget.tracker.repository.CategoryRepository;
import com.budget.tracker.repository.UserRepository;
import com.budget.tracker.payload.request.TransferRequest;
import com.budget.tracker.service.CategoryService;
import com.budget.tracker.service.LabelService;
import com.budget.tracker.service.TransactionService;
import com.budget.tracker.service.TransferService;
import com.budget.tracker.service.UserPreferenceService;
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

    private static final UUID DEMO_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private final TransactionService transactionService;
    private final TransferService transferService;
    private final UserPreferenceService userPreferenceService;

    public DataSeeder(UserRepository userRepository,
                      AccountRepository accountRepository,
                      CategoryRepository categoryRepository,
                      PasswordEncoder passwordEncoder,
                      CategoryService categoryService,
                      LabelService labelService,
                      TransactionService transactionService,
                      TransferService transferService,
                      UserPreferenceService userPreferenceService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryService = categoryService;
        this.labelService = labelService;
        this.transactionService = transactionService;
        this.transferService = transferService;
        this.userPreferenceService = userPreferenceService;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        String testEmail = "test@example.com";

        if (userRepository.existsByEmail(testEmail)) {
            System.out.println("Demo user already exists, skipping seeding.");
            return;
        }

        System.out.println("Seeding demo data for user: " + testEmail + " (ID: " + DEMO_USER_ID + ")...");

        // 1. Create User with Fixed ID
        User user = new User();
        user.setId(DEMO_USER_ID);
        user.setEmail(testEmail);
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setCreatedAt(OffsetDateTime.now());
        userRepository.save(user);
        
        // Set AuthContext for services to work
        AuthContext.setUserId(DEMO_USER_ID);

        try {
            // 2. Initialize Defaults
            categoryService.initializeDefaultCategories(DEMO_USER_ID);
            labelService.initializeDefaultLabels(DEMO_USER_ID);
            
            // 3. Initialize Preferences
            UserPreference prefs = new UserPreference();
            prefs.setCurrencySymbol("₹");
            userPreferenceService.updatePreferences(DEMO_USER_ID, prefs);

            // 4. Create Accounts
            Account mainBank = createAccount(DEMO_USER_ID, "Main Bank", AccountType.BANK, new BigDecimal("2500.00"), null);
            Account cash = createAccount(DEMO_USER_ID, "Cash", AccountType.CASH, new BigDecimal("120.50"), null);
            createAccount(DEMO_USER_ID, "Visa Credit", AccountType.CREDIT_CARD, new BigDecimal("450.00"), new BigDecimal("5000.00"));
            createAccount(DEMO_USER_ID, "Bob (Lend)", AccountType.FRIEND_LENDING, new BigDecimal("50.00"), null);

            // 5. Create Transactions

            // Find a category for Food
            Category foodCategory = categoryRepository.findAllByUserId(DEMO_USER_ID).stream()
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
            TransferRequest transferReq = new TransferRequest();
            transferReq.setFromAccountId(mainBank.getId());
            transferReq.setToAccountId(cash.getId());
            transferReq.setFromAmount(new BigDecimal("50.00"));
            transferReq.setAdjustment(new BigDecimal("5.00")); // showcases adjustment feature
            transferReq.setDescription("ATM Withdrawal");
            transferReq.setTransactionDate(OffsetDateTime.now());
            transferService.createTransfer(transferReq);

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
