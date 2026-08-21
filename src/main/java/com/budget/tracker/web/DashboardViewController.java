package com.budget.tracker.web;

import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.payload.response.ActivityResponse;
import com.budget.tracker.service.AccountService;
import com.budget.tracker.service.ActivityService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class DashboardViewController {

    private final AccountService accountService;
    private final ActivityService activityService;

    public DashboardViewController(AccountService accountService, ActivityService activityService) {
        this.accountService = accountService;
        this.activityService = activityService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("activePage", "dashboard");
        addAccountAttributes(model);
        addRecentAttributes(model);
        addInsightsAttributes(model);
        return "dashboard";
    }

    @GetMapping("/dashboard/sections")
    public String sections(Model model) {
        addAccountAttributes(model);
        addRecentAttributes(model);
        addInsightsAttributes(model);
        return "fragments/dashboard-sections";
    }

    @GetMapping("/dashboard/accounts")
    public String accounts(Model model) {
        addAccountAttributes(model);
        return "fragments/account-list";
    }

    @GetMapping("/dashboard/recent")
    public String recent(Model model) {
        addRecentAttributes(model);
        return "fragments/recent-transactions";
    }

    @GetMapping("/accounts/form")
    public String createAccountForm(Model model) {
        model.addAttribute("accountForm", new AccountForm());
        model.addAttribute("mode", "create");
        return "fragments/account-form";
    }

    @GetMapping("/accounts/{id}/edit")
    public String editAccountForm(@PathVariable UUID id, Model model) {
        Account account = accountService.getAccountById(id);
        AccountForm form = new AccountForm();
        form.setName(account.getName());
        form.setType(account.getType());
        BigDecimal initial = account.getInitialBalance() != null ? account.getInitialBalance() : account.getBalance();
        form.setInitialBalance(initial.abs());
        form.setCreditLimit(account.getCreditLimit());
        boolean owesThem = account.getType() == AccountType.FRIEND_LENDING
                && (account.getBalance() != null && account.getBalance().signum() < 0);
        form.setLendingDirection(owesThem
                ? AccountForm.LendingDirection.I_OWE_THEM
                : AccountForm.LendingDirection.THEY_OWE_ME);
        model.addAttribute("accountForm", form);
        model.addAttribute("accountId", id);
        model.addAttribute("mode", "edit");
        return "fragments/account-form";
    }

    @PostMapping("/accounts")
    public String createAccount(@Valid @ModelAttribute("accountForm") AccountForm form,
                                BindingResult result, HttpServletResponse response, Model model) {
        if (result.hasErrors()) {
            return formErrors(result, model, "create", response);
        }
        accountService.createAccount(toAccount(form, null));
        return accountSectionSuccess(response, model, "Account created successfully");
    }

    @PutMapping("/accounts/{id}")
    public String updateAccount(@PathVariable UUID id,
                                @Valid @ModelAttribute("accountForm") AccountForm form,
                                BindingResult result, HttpServletResponse response, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("accountId", id);
            return formErrors(result, model, "edit", response);
        }
        accountService.updateAccount(id, toAccount(form, id));
        return accountSectionSuccess(response, model, "Account updated successfully");
    }

    @DeleteMapping("/accounts/{id}")
    public String deleteAccount(@PathVariable UUID id, HttpServletResponse response, Model model) {
        accountService.deleteAccount(id);
        return accountSectionSuccess(response, model, "Account deleted");
    }

    private String formErrors(BindingResult result, Model model, String mode, HttpServletResponse response) {
        model.addAttribute("errors", result);
        model.addAttribute("mode", mode);
        response.setHeader("HX-Retarget", "#modal-content");
        return "fragments/account-form";
    }

    private String accountSectionSuccess(HttpServletResponse response, Model model, String toastMessage) {
        response.setHeader("HX-Trigger",
                "{\"closeModal\":\"\",\"toast-success\":\"" + toastMessage + "\"}");
        addAccountAttributes(model);
        return "fragments/account-list";
    }

    private Account toAccount(AccountForm form, UUID id) {
        Account account = new Account();
        if (id != null) {
            account.setId(id);
        }
        account.setName(form.getName());
        account.setType(form.getType());
        BigDecimal balance = form.getInitialBalance() == null
                ? BigDecimal.ZERO
                : form.getInitialBalance();
        if (form.getType() == AccountType.FRIEND_LENDING
                && form.getLendingDirection() == AccountForm.LendingDirection.I_OWE_THEM) {
            balance = balance.abs().negate();
        }
        account.setInitialBalance(balance);
        account.setBalance(balance);
        account.setCreditLimit(form.getType() == AccountType.CREDIT_CARD ? form.getCreditLimit() : null);
        return account;
    }

    private void addAccountAttributes(Model model) {
        List<Account> accounts = accountService.getAllAccountsForUser();
        BigDecimal totalBalance = accounts.stream()
                .map(account -> account.getType() == AccountType.CREDIT_CARD
                        ? account.getBalance().negate()
                        : account.getBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<AccountType, List<Account>> grouped = new LinkedHashMap<>();
        for (Account account : accounts) {
            grouped.computeIfAbsent(account.getType(), key -> new ArrayList<>()).add(account);
        }
        model.addAttribute("accounts", accounts);
        model.addAttribute("groupedAccounts", grouped);
        model.addAttribute("totalBalance", totalBalance);
    }

    private void addRecentAttributes(Model model) {
        model.addAttribute("activity", activityService.getActivity(
                null, null, null, null, null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "transactionDate"))));
    }

    private void addInsightsAttributes(Model model) {
        List<ActivityResponse> items = activityService.getActivity(
                null, null, null, null, null,
                PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "transactionDate"))).getContent();

        Map<String, BigDecimal> byLabel = new LinkedHashMap<>();
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (ActivityResponse item : items) {
            if (!"EXPENSE".equals(item.getType()) && !"LEND".equals(item.getType())) continue;
            BigDecimal amount = item.getAmount() == null ? BigDecimal.ZERO : item.getAmount().abs();
            if (item.getLabels() == null || item.getLabels().isEmpty()) {
                byLabel.merge("Unlabeled", amount, BigDecimal::add);
            } else {
                for (com.budget.tracker.model.Label label : item.getLabels()) {
                    byLabel.merge(label.getName(), amount, BigDecimal::add);
                }
            }
            String category = item.getCategory() != null && item.getCategory().getName() != null
                    ? item.getCategory().getName() : "Uncategorized";
            byCategory.merge(category, amount, BigDecimal::add);
        }

        List<Map.Entry<String, BigDecimal>> labelEntries = byLabel.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry<String, BigDecimal>::getValue, Comparator.reverseOrder()))
                .toList();
        List<Map.Entry<String, BigDecimal>> categoryEntries = byCategory.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry<String, BigDecimal>::getValue, Comparator.reverseOrder()))
                .limit(8)
                .toList();

        BigDecimal total = labelEntries.stream().map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("insightLabels", toInsightRows(labelEntries, total));
        model.addAttribute("insightCategories", toInsightRows(categoryEntries, total));
    }

    private List<Map<String, Object>> toInsightRows(List<Map.Entry<String, BigDecimal>> entries, BigDecimal total) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", entry.getKey());
            row.put("value", entry.getValue());
            int pct = total.signum() == 0 ? 0
                    : entry.getValue().multiply(BigDecimal.valueOf(100))
                        .divide(total, 0, java.math.RoundingMode.HALF_UP).intValue();
            row.put("pct", pct);
            rows.add(row);
        }
        return rows;
    }
}
