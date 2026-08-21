package com.budget.tracker.web;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.Category;
import com.budget.tracker.model.Label;
import com.budget.tracker.model.TransactionType;
import com.budget.tracker.model.UserPreference;
import com.budget.tracker.payload.request.TransactionRequest;
import com.budget.tracker.payload.request.TransferRequest;
import com.budget.tracker.payload.response.ActivityResponse;
import com.budget.tracker.service.AccountService;
import com.budget.tracker.service.ActivityService;
import com.budget.tracker.service.CategoryService;
import com.budget.tracker.service.LabelService;
import com.budget.tracker.service.TransactionService;
import com.budget.tracker.service.TransferService;
import com.budget.tracker.service.UserPreferenceService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class TransactionsViewController {

    private static final int LIST_PAGE_SIZE = 20;
    private static final List<String> TYPE_OPTIONS = List.of("EXPENSE", "INCOME", "TRANSFER", "LEND", "BORROW");

    private final ActivityService activityService;
    private final TransactionService transactionService;
    private final TransferService transferService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final LabelService labelService;
    private final UserPreferenceService userPreferenceService;

    public TransactionsViewController(ActivityService activityService,
                                      TransactionService transactionService,
                                      TransferService transferService,
                                      AccountService accountService,
                                      CategoryService categoryService,
                                      LabelService labelService,
                                      UserPreferenceService userPreferenceService) {
        this.activityService = activityService;
        this.transactionService = transactionService;
        this.transferService = transferService;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.labelService = labelService;
        this.userPreferenceService = userPreferenceService;
    }

    @GetMapping("/transactions")
    public String page(@RequestParam(required = false) String search,
                       @RequestParam(required = false) String type,
                       @RequestParam(required = false) String accountId,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate,
                       Model model) {
        model.addAttribute("activePage", "transactions");
        addFilterAttributes(model, blankToNull(search), blankToNull(type),
                parseUuid(accountId), parseLocalDate(startDate), parseLocalDate(endDate));
        addListAttributes(model, blankToNull(search), blankToNull(type),
                parseUuid(accountId), parseLocalDate(startDate), parseLocalDate(endDate), 0);
        return "transactions";
    }

    @GetMapping("/transactions/list")
    public String list(@RequestParam(required = false) String search,
                       @RequestParam(required = false) String type,
                       @RequestParam(required = false) String accountId,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        addListAttributes(model, blankToNull(search), blankToNull(type),
                parseUuid(accountId), parseLocalDate(startDate), parseLocalDate(endDate), page);
        return "fragments/transaction-list";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private LocalDate parseLocalDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    @GetMapping("/transactions/form")
    public String createForm(Model model) {
        addFormAttributes(model, null);
        model.addAttribute("mode", "create");
        return "fragments/transaction-form";
    }

    @GetMapping("/transfers/form")
    public String createTransferForm(Model model) {
        addFormAttributes(model, null);
        model.addAttribute("mode", "create");
        return "fragments/transaction-form";
    }

    @GetMapping("/transactions/{id}/edit")
    public String editTransaction(@PathVariable UUID id, Model model) {
        ActivityResponse item = activityService.getActivityById(id);
        addFormAttributes(model, item);
        model.addAttribute("mode", "edit");
        return "fragments/transaction-form";
    }

    @GetMapping("/transfers/{id}/edit")
    public String editTransfer(@PathVariable UUID id, Model model) {
        ActivityResponse item = activityService.getActivityById(id);
        addFormAttributes(model, item);
        model.addAttribute("mode", "edit");
        return "fragments/transaction-form";
    }

    @PostMapping("/transactions")
    public ResponseEntity<String> createTransaction(@RequestParam String type,
                                    @RequestParam(required = false) BigDecimal amount,
                                    @RequestParam String transactionDate,
                                    @RequestParam(required = false) UUID accountId,
                                    @RequestParam(required = false) UUID fromAccountId,
                                    @RequestParam(required = false) UUID toAccountId,
                                    @RequestParam(required = false) BigDecimal fromAmount,
                                    @RequestParam(required = false) BigDecimal toAmount,
                                    @RequestParam(required = false) BigDecimal adjustment,
                                    @RequestParam(required = false) UUID categoryId,
                                    @RequestParam(required = false) String description,
                                    @RequestParam(required = false) List<UUID> labelIds,
                                    HttpServletResponse response) {
        try {
            if ("TRANSFER".equals(type)) {
                transferService.createTransfer(toTransferRequest(fromAccountId != null ? fromAccountId : accountId, toAccountId, fromAmount, toAmount, adjustment, transactionDate, description, categoryId, labelIds));
            } else {
                transactionService.createTransaction(toTransactionRequest(type, amount, transactionDate, accountId, categoryId, description, labelIds));
            }
        } catch (RuntimeException e) {
            return errorToast(e, response);
        }
        return saveSuccess(response, "Transaction created successfully");
    }

    @PostMapping("/transfers")
    public ResponseEntity<String> createTransfer(@RequestParam String type,
                                 @RequestParam(required = false) BigDecimal amount,
                                 @RequestParam String transactionDate,
                                 @RequestParam(required = false) UUID accountId,
                                 @RequestParam(required = false) UUID fromAccountId,
                                 @RequestParam(required = false) UUID toAccountId,
                                 @RequestParam(required = false) BigDecimal fromAmount,
                                 @RequestParam(required = false) BigDecimal toAmount,
                                 @RequestParam(required = false) BigDecimal adjustment,
                                 @RequestParam(required = false) UUID categoryId,
                                 @RequestParam(required = false) String description,
                                 @RequestParam(required = false) List<UUID> labelIds,
                                 HttpServletResponse response) {
        try {
            transferService.createTransfer(toTransferRequest(fromAccountId != null ? fromAccountId : accountId, toAccountId, fromAmount, toAmount, adjustment, transactionDate, description, categoryId, labelIds));
        } catch (RuntimeException e) {
            return errorToast(e, response);
        }
        return saveSuccess(response, "Transaction created successfully");
    }

    @PutMapping("/transactions/{id}")
    public ResponseEntity<String> updateTransaction(@PathVariable UUID id,
                                    @RequestParam String type,
                                    @RequestParam(required = false) BigDecimal amount,
                                    @RequestParam String transactionDate,
                                    @RequestParam(required = false) UUID accountId,
                                    @RequestParam(required = false) UUID fromAccountId,
                                    @RequestParam(required = false) UUID toAccountId,
                                    @RequestParam(required = false) BigDecimal fromAmount,
                                    @RequestParam(required = false) BigDecimal toAmount,
                                    @RequestParam(required = false) BigDecimal adjustment,
                                    @RequestParam(required = false) UUID categoryId,
                                    @RequestParam(required = false) String description,
                                    @RequestParam(required = false) List<UUID> labelIds,
                                    HttpServletResponse response) {
        try {
            transactionService.updateTransaction(id, toTransactionRequest(type, amount, transactionDate, accountId, categoryId, description, labelIds));
        } catch (RuntimeException e) {
            return errorToast(e, response);
        }
        return saveSuccess(response, "Transaction updated successfully");
    }

    @PutMapping("/transfers/{id}")
    public ResponseEntity<String> updateTransfer(@PathVariable UUID id,
                                 @RequestParam String type,
                                 @RequestParam(required = false) BigDecimal amount,
                                 @RequestParam String transactionDate,
                                 @RequestParam(required = false) UUID accountId,
                                 @RequestParam(required = false) UUID fromAccountId,
                                 @RequestParam(required = false) UUID toAccountId,
                                 @RequestParam(required = false) BigDecimal fromAmount,
                                 @RequestParam(required = false) BigDecimal toAmount,
                                 @RequestParam(required = false) BigDecimal adjustment,
                                 @RequestParam(required = false) UUID categoryId,
                                 @RequestParam(required = false) String description,
                                 @RequestParam(required = false) List<UUID> labelIds,
                                 HttpServletResponse response) {
        try {
            transferService.updateTransfer(id, toTransferRequest(fromAccountId != null ? fromAccountId : accountId, toAccountId, fromAmount, toAmount, adjustment, transactionDate, description, categoryId, labelIds));
        } catch (RuntimeException e) {
            return errorToast(e, response);
        }
        return saveSuccess(response, "Transaction updated successfully");
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<String> deleteTransaction(@PathVariable UUID id, HttpServletResponse response) {
        try {
            transactionService.deleteTransaction(id);
        } catch (RuntimeException e) {
            return errorToast(e, response);
        }
        return saveSuccess(response, "Transaction deleted successfully");
    }

    @DeleteMapping("/transfers/{id}")
    public ResponseEntity<String> deleteTransfer(@PathVariable UUID id, HttpServletResponse response) {
        try {
            transferService.deleteTransfer(id);
        } catch (RuntimeException e) {
            return errorToast(e, response);
        }
        return saveSuccess(response, "Transaction deleted successfully");
    }

    private ResponseEntity<String> saveSuccess(HttpServletResponse response, String toastMessage) {
        response.setHeader("HX-Trigger",
                "{\"closeModal\":\"\",\"toast-success\":\"" + toastMessage + "\",\"refreshAfterSave\":\"\"}");
        return org.springframework.http.ResponseEntity.ok().body("");
    }

    private ResponseEntity<String> errorToast(RuntimeException e, HttpServletResponse response) {
        response.setHeader("HX-Trigger",
                "{\"toast-error\":\"" + escapeJson(e.getMessage() == null ? "Something went wrong" : e.getMessage()) + "\"}");
        return org.springframework.http.ResponseEntity.ok().body("");
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    private TransactionRequest toTransactionRequest(String type, BigDecimal amount, String date,
                                                    UUID accountId, UUID categoryId, String description, List<UUID> labelIds) {
        TransactionRequest request = new TransactionRequest();
        request.setType(TransactionType.valueOf(type));
        request.setAmount(amount);
        request.setAccountId(accountId);
        request.setCategoryId(categoryId);
        request.setDescription(description);
        request.setLabelIds(labelIds);
        request.setTransactionDate(parseDate(date));
        return request;
    }

    private TransferRequest toTransferRequest(UUID fromAccountId, UUID toAccountId,
                                              BigDecimal fromAmount, BigDecimal toAmount, BigDecimal adjustment,
                                              String date, String description, UUID categoryId, List<UUID> labelIds) {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(fromAccountId);
        request.setToAccountId(toAccountId);
        request.setFromAmount(fromAmount);
        request.setToAmount(toAmount);
        request.setAdjustment(adjustment);
        request.setDescription(description);
        request.setCategoryId(categoryId);
        request.setLabelIds(labelIds);
        request.setTransactionDate(parseDate(date));
        return request;
    }

    private OffsetDateTime parseDate(String date) {
        return LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
    }

    private String formNumber(BigDecimal value) {
        if (value == null) return "";
        return value.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private void addFilterAttributes(Model model, String search, String type, UUID accountId,
                                     LocalDate startDate, LocalDate endDate) {
        model.addAttribute("filterSearch", search);
        model.addAttribute("filterType", type);
        model.addAttribute("filterAccountId", accountId);
        model.addAttribute("filterStartDate", startDate == null ? null : startDate.toString());
        model.addAttribute("filterEndDate", endDate == null ? null : endDate.toString());
        model.addAttribute("typeOptions", TYPE_OPTIONS);
        model.addAttribute("accounts", accountService.getAllAccountsForUser());
    }

    private void addListAttributes(Model model, String search, String type, UUID accountId,
                                   LocalDate startDate, LocalDate endDate, int page) {
        OffsetDateTime start = startDate == null ? null : startDate.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime end = endDate == null ? null : endDate.atTime(LocalTime.of(23, 59, 59)).atOffset(ZoneOffset.UTC);
        Page<ActivityResponse> items = activityService.getActivity(
                search, type, accountId, start, end,
                PageRequest.of(page, LIST_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "transactionDate")));
        model.addAttribute("txPage", items);
        if (items.hasNext()) {
            StringBuilder qs = new StringBuilder();
            appendParam(qs, "search", search);
            appendParam(qs, "type", type);
            if (accountId != null) appendParam(qs, "accountId", accountId.toString());
            if (startDate != null) appendParam(qs, "startDate", startDate.toString());
            if (endDate != null) appendParam(qs, "endDate", endDate.toString());
            appendParam(qs, "page", String.valueOf(page + 1));
            model.addAttribute("nextListUrl", "/transactions/list?" + qs);
        } else {
            model.addAttribute("nextListUrl", null);
        }
    }

    private void appendParam(StringBuilder qs, String name, String value) {
        if (qs.length() > 0) qs.append('&');
        qs.append(name).append('=').append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8));
    }

    private void addFormAttributes(Model model, ActivityResponse item) {
        model.addAttribute("item", item);
        List<Account> accounts = accountService.getAllAccountsForUser();
        List<Category> categories = categoryService.getAllCategoriesForUser();
        List<Label> labels = labelService.getAllLabelsForUser();
        UserPreference preference = userPreferenceService.getPreferences(AuthContext.getUserId());

        model.addAttribute("accounts", accounts);
        model.addAttribute("categories", categories);
        model.addAttribute("labels", labels);
        List<Map<String, String>> labelData = new ArrayList<>();
        for (Label label : labels) {
            Map<String, String> entry = new java.util.LinkedHashMap<>();
            entry.put("id", label.getId().toString());
            entry.put("name", label.getName());
            labelData.add(entry);
        }
        model.addAttribute("labelData", labelData);

        if (item != null) {
            model.addAttribute("formAmount", formNumber(item.getAmount()));
            model.addAttribute("formFromAmount", formNumber(item.getFromAmount()));
            model.addAttribute("formToAmount", formNumber(item.getToAmount()));
            model.addAttribute("formAdjustment", formNumber(item.getAdjustment()));
        } else {
            model.addAttribute("formAmount", "");
            model.addAttribute("formFromAmount", "");
            model.addAttribute("formToAmount", "");
            model.addAttribute("formAdjustment", "");
        }

        boolean isTransfer = item != null && "TRANSFER".equals(item.getKind());
        if (item == null) {
            UUID prefAccount = preference.getDefaultAccountId();
            UUID defaultAccount = prefAccount != null && accounts.stream().anyMatch(a -> a.getId().equals(prefAccount))
                    ? prefAccount
                    : (accounts.isEmpty() ? null : accounts.get(0).getId());
            TransactionType defaultType = preference.getDefaultTransactionType() != null
                    ? preference.getDefaultTransactionType() : TransactionType.EXPENSE;
            UUID prefCategory = preference.getDefaultCategoryId();
            UUID defaultCategory = prefCategory != null && categories.stream().anyMatch(c -> c.getId().equals(prefCategory))
                    ? prefCategory
                    : (categories.isEmpty() ? null : categories.get(0).getId());
            UUID prefLabel = preference.getDefaultLabelId();
            UUID defaultLabel = prefLabel != null && labels.stream().anyMatch(l -> l.getId().equals(prefLabel))
                    ? prefLabel
                    : labels.stream().filter(l -> l.isDefault()).map(Label::getId).findFirst()
                        .orElseGet(() -> labels.isEmpty() ? null : labels.get(0).getId());
            model.addAttribute("formAccountId", defaultAccount);
            model.addAttribute("formType", defaultType.name());
            model.addAttribute("formCategoryId", defaultCategory);
            model.addAttribute("formLabelIds", defaultLabel == null ? List.of() : List.of(defaultLabel));
        } else {
            model.addAttribute("formAccountId", item.getAccount() == null ? null : item.getAccount().getId());
            model.addAttribute("formType", item.getType());
            model.addAttribute("formCategoryId", item.getCategory() == null ? null : item.getCategory().getId());
            model.addAttribute("formLabelIds", item.getLabels() == null ? List.of()
                    : item.getLabels().stream().map(Label::getId).collect(Collectors.toList()));
        }
        model.addAttribute("isTransferEdit", isTransfer);
        model.addAttribute("showTransferOption", item == null || isTransfer);
        model.addAttribute("formDate", item == null
                ? LocalDate.now().toString()
                : item.getTransactionDate().toLocalDate().toString());
    }
}
