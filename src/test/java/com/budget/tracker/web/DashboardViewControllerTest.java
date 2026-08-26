package com.budget.tracker.web;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.Account;
import com.budget.tracker.model.AccountType;
import com.budget.tracker.model.User;
import com.budget.tracker.model.UserPreference;
import com.budget.tracker.payload.response.ExpenditureSummaryResponse;
import com.budget.tracker.repository.UserRepository;
import com.budget.tracker.security.UserDetailsImpl;
import com.budget.tracker.service.AccountService;
import com.budget.tracker.service.ActivityService;
import com.budget.tracker.service.ExpenditureSummaryService;
import com.budget.tracker.service.UserPreferenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DashboardViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @MockBean
    private ActivityService activityService;

    @MockBean
    private ExpenditureSummaryService expenditureSummaryService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserPreferenceService userPreferenceService;

    private UUID userId;
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userDetails = new UserDetailsImpl(userId, "test@test.com", "password");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        AuthContext.setUserId(userId);

        // Stub userrepository and preferences for PageContextInterceptor to create fmt
        User user = new User();
        user.setId(userId);
        user.setEmail("test@test.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        UserPreference pref = new UserPreference();
        pref.setCurrencySymbol("₹");
        when(userPreferenceService.getPreferences(userId)).thenReturn(pref);

        // Default stubs
        when(accountService.getAllAccountsForUser()).thenReturn(List.of());
        ExpenditureSummaryResponse summary = new ExpenditureSummaryResponse();
        summary.setYesterday(BigDecimal.ZERO);
        summary.setToday(BigDecimal.ZERO);
        summary.setLastWeek(BigDecimal.ZERO);
        summary.setThisWeek(BigDecimal.ZERO);
        summary.setLastMonth(BigDecimal.ZERO);
        summary.setThisMonth(BigDecimal.ZERO);
        when(expenditureSummaryService.getSummary()).thenReturn(summary);
        when(activityService.getActivity(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(10), 0));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void dashboard_shouldRenderWithoutSpendingInsights() throws Exception {
        mockMvc.perform(get("/dashboard").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(content().string(not(containsString("Spending Insights"))))
                .andExpect(content().string(not(containsString("insights-section"))))
                .andExpect(content().string(not(containsString("Spending by Label"))))
                .andExpect(content().string(not(containsString("Top Categories"))))
                .andExpect(content().string(not(containsString("insight-bar"))));
    }

    @Test
    void dashboard_shouldNotExposeInsightModelAttributes() throws Exception {
        mockMvc.perform(get("/dashboard").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("insightLabels"))
                .andExpect(model().attributeDoesNotExist("insightCategories"));
    }

    @Test
    void dashboard_shouldStillProvideCoreAttributes() throws Exception {
        Account acc = new Account();
        acc.setId(UUID.randomUUID());
        acc.setName("Cash");
        acc.setType(AccountType.CASH);
        acc.setBalance(new BigDecimal("100.00"));
        when(accountService.getAllAccountsForUser()).thenReturn(List.of(acc));

        mockMvc.perform(get("/dashboard").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("accounts"))
                .andExpect(model().attributeExists("groupedAccounts"))
                .andExpect(model().attributeExists("totalBalance"))
                .andExpect(model().attributeExists("expenditureSummary"))
                .andExpect(model().attributeExists("periodRanges"))
                .andExpect(model().attributeExists("activity"));
    }

    @Test
    void dashboardSections_fragmentShouldRenderWithoutInsights() throws Exception {
        mockMvc.perform(get("/dashboard/sections").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Spending Insights"))))
                .andExpect(content().string(not(containsString("insights-section"))))
                .andExpect(content().string(not(containsString("insight-bar"))));
    }

    @Test
    void dashboardSections_shouldNotExposeInsightModelAttributes() throws Exception {
        mockMvc.perform(get("/dashboard/sections").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("insightLabels"))
                .andExpect(model().attributeDoesNotExist("insightCategories"));
    }

    @Test
    void dashboard_shouldNotFetch1000ActivitiesForInsights() throws Exception {
        // After removal, only addRecentAttributes fetches 10 items, not 1000
        mockMvc.perform(get("/dashboard").with(user(userDetails)))
                .andExpect(status().isOk());

        verify(activityService, times(1)).getActivity(any(), any(), any(), any(), any(), argThat(pageable ->
                pageable.getPageSize() == 10));
        // Verify no call with 1000
        verify(activityService, never()).getActivity(any(), any(), any(), any(), any(), argThat(pageable ->
                pageable.getPageSize() == 1000));
    }

    @Test
    void dashboardSections_shouldNotFetch1000ActivitiesForInsights() throws Exception {
        mockMvc.perform(get("/dashboard/sections").with(user(userDetails)))
                .andExpect(status().isOk());

        verify(activityService, times(1)).getActivity(any(), any(), any(), any(), any(), argThat(pageable ->
                pageable.getPageSize() == 10));
        verify(activityService, never()).getActivity(any(), any(), any(), any(), any(), argThat(pageable ->
                pageable.getPageSize() == 1000));
    }

    @Test
    void dashboard_shouldStillContainAccountsPeriodAndRecentSections() throws Exception {
        mockMvc.perform(get("/dashboard").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dashboard-content")))
                .andExpect(content().string(containsString("period-grid")))
                .andExpect(content().string(containsString("Expenditure")));
    }

    @Test
    void dashboardAccountsFragment_shouldStillWork() throws Exception {
        mockMvc.perform(get("/dashboard/accounts").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Spending Insights"))));
    }

    @Test
    void dashboardRecentFragment_shouldStillWork() throws Exception {
        mockMvc.perform(get("/dashboard/recent").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Spending Insights"))));
    }

    @Test
    void cssAndTemplate_shouldNotContainInsightStyles() throws Exception {
        // Verify static/template contracts via direct file checks is done via unit assertions above,
        // but also ensure dashboard rendering does not leak insight CSS classes
        mockMvc.perform(get("/dashboard").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("insight-card"))))
                .andExpect(content().string(not(containsString("insight-list"))))
                .andExpect(content().string(not(containsString("insight-label"))));
    }
}
