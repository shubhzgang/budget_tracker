package com.budget.tracker.web;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.Category;
import com.budget.tracker.model.Label;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.model.UserPreference;
import com.budget.tracker.service.AccountService;
import com.budget.tracker.service.BackupService;
import com.budget.tracker.service.CategoryService;
import com.budget.tracker.service.LabelService;
import com.budget.tracker.service.UserPreferenceService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@Controller
public class SettingsViewController {

    private static final List<String> TYPE_OPTIONS = List.of("EXPENSE", "INCOME", "TRANSFER", "LEND", "BORROW");

    private final CategoryService categoryService;
    private final LabelService labelService;
    private final AccountService accountService;
    private final UserPreferenceService userPreferenceService;
    private final BackupService backupService;

    public SettingsViewController(CategoryService categoryService,
                                  LabelService labelService,
                                  AccountService accountService,
                                  UserPreferenceService userPreferenceService,
                                  BackupService backupService) {
        this.categoryService = categoryService;
        this.labelService = labelService;
        this.accountService = accountService;
        this.userPreferenceService = userPreferenceService;
        this.backupService = backupService;
    }

    @GetMapping("/settings")
    public String page(Model model) {
        model.addAttribute("activePage", "settings");
        // All four panels render server-side (tabs toggle client-side, like the React app)
        addCategoryAttributes(model, null);
        addLabelAttributes(model, null);
        addDefaultsAttributes(model);
        addBackupAttributes(model);
        return "settings";
    }

    // ── Categories tab ────────────────────────────────────────────────────────

    @GetMapping("/settings/categories")
    public String categories(Model model) {
        addCategoryAttributes(model, null);
        return "fragments/category-manager";
    }

    @PostMapping("/settings/categories")
    public String createCategory(@RequestParam String name,
                                 @RequestParam(defaultValue = "😀") String icon,
                                 Model model,
                                 HttpServletResponse response) {
        String error = null;
        try {
            Category category = new Category();
            category.setName(name);
            category.setIcon(icon);
            categoryService.createCategory(category);
            response.setHeader("HX-Trigger", "{\"toast-success\":\"Category added successfully\"}");
        } catch (IllegalArgumentException e) {
            error = e.getMessage();
        }
        addCategoryAttributes(model, error);
        return "fragments/category-manager";
    }

    @DeleteMapping("/settings/categories/{id}")
    public String deleteCategory(@PathVariable UUID id,
                                 Model model,
                                 HttpServletResponse response) {
        categoryService.deleteCategory(id);
        response.setHeader("HX-Trigger", "{\"toast-success\":\"Category deleted\"}");
        addCategoryAttributes(model, null);
        return "fragments/category-manager";
    }

    // ── Labels tab ────────────────────────────────────────────────────────────

    @GetMapping("/settings/labels")
    public String labels(Model model) {
        addLabelAttributes(model, null);
        return "fragments/label-manager";
    }

    @PostMapping("/settings/labels")
    public String createLabel(@RequestParam String name,
                              Model model,
                              HttpServletResponse response) {
        String error = null;
        if (name != null && name.contains("|")) {
            // Same message as the React client-side validation (E2E contract)
            error = "Label name cannot contain '|'";
        } else {
            try {
                Label label = new Label();
                label.setName(name);
                labelService.createLabel(label);
                response.setHeader("HX-Trigger", "{\"toast-success\":\"Label added successfully\"}");
            } catch (IllegalArgumentException e) {
                error = e.getMessage();
            }
        }
        addLabelAttributes(model, error);
        return "fragments/label-manager";
    }

    @DeleteMapping("/settings/labels/{id}")
    public String deleteLabel(@PathVariable UUID id,
                              Model model,
                              HttpServletResponse response) {
        labelService.deleteLabel(id);
        response.setHeader("HX-Trigger", "{\"toast-success\":\"Label deleted\"}");
        addLabelAttributes(model, null);
        return "fragments/label-manager";
    }

    // ── Defaults tab ──────────────────────────────────────────────────────────

    @GetMapping("/settings/defaults")
    public String defaults(Model model) {
        addDefaultsAttributes(model);
        return "fragments/defaults-form";
    }

    @PutMapping("/settings/preferences")
    public String savePreferences(@RequestParam(required = false) String defaultAccountId,
                                  @RequestParam(required = false) String defaultTransactionType,
                                  @RequestParam(required = false) String defaultCategoryId,
                                  @RequestParam(required = false) String defaultLabelId,
                                  @RequestParam(required = false) String currencySymbol,
                                  @RequestParam(required = false) Boolean autoBackupEnabled,
                                  @RequestParam(required = false) String autoBackupFrequency,
                                  @RequestParam(required = false) String autoBackupFormat,
                                  Model model,
                                  HttpServletResponse response) {
        UUID userId = AuthContext.getUserId();
        UserPreference prefs = new UserPreference();
        prefs.setUserId(userId);
        prefs.setDefaultAccountId(parseUuid(defaultAccountId));
        prefs.setDefaultTransactionType(parseType(defaultTransactionType));
        prefs.setDefaultCategoryId(parseUuid(defaultCategoryId));
        prefs.setDefaultLabelId(parseUuid(defaultLabelId));
        prefs.setCurrencySymbol(currencySymbol == null || currencySymbol.isBlank() ? "₹" : currencySymbol);
        prefs.setAutoBackupEnabled(Boolean.TRUE.equals(autoBackupEnabled));
        prefs.setAutoBackupFrequency(autoBackupFrequency);
        prefs.setAutoBackupFormat(autoBackupFormat);
        userPreferenceService.updatePreferences(userId, prefs);
        response.setHeader("HX-Trigger", "{\"toast-success\":\"Preferences saved successfully\"}");
        addDefaultsAttributes(model);
        return "fragments/defaults-form";
    }

    // ── Data & Backup tab ─────────────────────────────────────────────────────

    @GetMapping("/settings/data")
    public String data(Model model) {
        addBackupAttributes(model);
        return "fragments/backup-manager";
    }

    private void addBackupAttributes(Model model) {
        model.addAttribute("backups", backupService.getBackupHistory(AuthContext.getUserId()));
    }

    // ── Model helpers ─────────────────────────────────────────────────────────

    private void addCategoryAttributes(Model model, String error) {
        List<Category> categories = categoryService.getAllCategoriesForUser();
        model.addAttribute("categories", categories);
        model.addAttribute("error", error);
    }

    private void addLabelAttributes(Model model, String error) {
        List<Label> labels = labelService.getAllLabelsForUser();
        model.addAttribute("labels", labels);
        model.addAttribute("error", error);
    }

    private void addDefaultsAttributes(Model model) {
        UUID userId = AuthContext.getUserId();
        UserPreference prefs = userPreferenceService.getPreferences(userId);
        List<Account> accounts = accountService.getAllAccountsForUser();
        model.addAttribute("prefs", prefs);
        model.addAttribute("accounts", accounts);
        model.addAttribute("categories", categoryService.getAllCategoriesForUser());
        model.addAttribute("labels", labelService.getAllLabelsForUser());
        model.addAttribute("typeOptions", TYPE_OPTIONS);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private TransactionType parseType(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return TransactionType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
