package com.budget.tracker.util;

import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.model.User;
import com.budget.tracker.repository.AccountRepository;
import com.budget.tracker.repository.UserRepository;
import com.budget.tracker.service.CategoryService;
import com.budget.tracker.service.LabelService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@Profile("demo")
@ConditionalOnProperty(name = "app.data-seeder.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryService categoryService;
    private final LabelService labelService;

    public DataSeeder(UserRepository userRepository, 
                      AccountRepository accountRepository, 
                      PasswordEncoder passwordEncoder,
                      CategoryService categoryService,
                      LabelService labelService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryService = categoryService;
        this.labelService = labelService;
    }

    @Override
    public void run(String... args) throws Exception {
        String testEmail = "test@example.com";
        
        if (userRepository.existsByEmail(testEmail)) {
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

        // 2. Initialize Defaults
        categoryService.initializeDefaultCategories(userId);
        labelService.initializeDefaultLabels(userId);

        // 3. Create Accounts
        createAccount(userId, "Main Bank", AccountType.BANK, new BigDecimal("2500.00"), null);
        createAccount(userId, "Cash", AccountType.CASH, new BigDecimal("120.50"), null);
        createAccount(userId, "Visa Credit", AccountType.CREDIT_CARD, new BigDecimal("450.00"), new BigDecimal("5000.00"));
        createAccount(userId, "Bob (Lending)", AccountType.FRIEND_LENDING, new BigDecimal("50.00"), null);

        System.out.println("Demo data seeded successfully.");
    }

    private void createAccount(UUID userId, String name, AccountType type, BigDecimal balance, BigDecimal creditLimit) {
        Account account = new Account();
        account.setUserId(userId);
        account.setName(name);
        account.setType(type);
        account.setBalance(balance);
        account.setInitialBalance(balance); // Fix: Set initial balance
        account.setCreditLimit(creditLimit);
        account.setCreatedAt(OffsetDateTime.now());
        accountRepository.save(account);
    }
}
