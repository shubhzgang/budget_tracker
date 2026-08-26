package com.budget.tracker.web;

import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.service.AccountService;
import com.budget.tracker.service.ActivityService;
import com.budget.tracker.service.ExpenditureSummaryService;
import com.budget.tracker.util.ExpenditurePeriods;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class DashboardViewController {

    private final AccountService accountService;
    private final ActivityService activityService;
    private final ExpenditureSummaryService expenditureSummaryService;

    public DashboardViewController(AccountService accountService, ActivityService activityService,
                                   ExpenditureSummaryService expenditureSummaryService) {
        this.accountService = accountService;
        this.activityService = activityService;
        this.expenditureSummaryService = expenditureSummaryService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("activePage", "dashboard");
        addAccountAttributes(model);
        addPeriodAttributes(model);
        addRecentAttributes(model);
        return "dashboard";
    }

    @GetMapping("/dashboard/sections")
    public String sections(Model model) {
        addAccountAttributes(model);
        addPeriodAttributes(model);
        addRecentAttributes(model);
        return "fragments/dashboard-sections";
    }

    private void addPeriodAttributes(Model model) {
        model.addAttribute("expenditureSummary", expenditureSummaryService.getSummary());
        model.addAttribute("periodRanges", ExpenditurePeriods.all());
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
}
