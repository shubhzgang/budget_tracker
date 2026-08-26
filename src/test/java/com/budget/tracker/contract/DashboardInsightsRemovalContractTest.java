package com.budget.tracker.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class DashboardInsightsRemovalContractTest {

    private String readFile(String relative) throws IOException {
        Path path = Paths.get(relative);
        assertTrue(Files.exists(path), "File should exist: " + relative);
        return Files.readString(path);
    }

    @Test
    void dashboardSectionsTemplate_shouldNotContainInsightsMarkup() throws Exception {
        String content = readFile("src/main/resources/templates/fragments/dashboard-sections.html");
        assertFalse(content.contains("insights-section"), "Template should not contain insights-section");
        assertFalse(content.contains("Spending Insights"), "Template should not contain Spending Insights");
        assertFalse(content.contains("Spending by Label"), "Template should not contain Spending by Label");
        assertFalse(content.contains("Top Categories"), "Template should not contain Top Categories");
        assertFalse(content.contains("insightLabels"), "Template should not contain insightLabels");
        assertFalse(content.contains("insightCategories"), "Template should not contain insightCategories");
        assertFalse(content.contains("insight-bar"), "Template should not contain insight-bar");
        // Should still contain core sections
        assertTrue(content.contains("account-list"), "Should still contain account-list");
        assertTrue(content.contains("period-cards"), "Should still contain period-cards");
        assertTrue(content.contains("recent-transactions"), "Should still contain recent-transactions");
        assertTrue(content.contains("dashboard-content"), "Should still contain dashboard-content");
    }

    @Test
    void dashboardSectionsTemplate_shouldOnlyContainThreeSections() throws Exception {
        String content = readFile("src/main/resources/templates/fragments/dashboard-sections.html");
        // Count occurrences of th:replace for core fragments - should be exactly 3
        int count = content.split("th:replace").length - 1;
        assertEquals(3, count, "Dashboard sections should contain exactly 3 th:replace includes (accounts, periods, recent)");
    }

    @Test
    void styleCss_shouldNotContainInsightStyles() throws Exception {
        String css = readFile("src/main/resources/static/css/style.css");
        assertFalse(css.contains(".insight-card"), "CSS should not contain .insight-card");
        assertFalse(css.contains(".insight-bar"), "CSS should not contain .insight-bar");
        assertFalse(css.contains(".insights-grid"), "CSS should not contain .insights-grid");
        assertFalse(css.contains(".insight-label"), "CSS should not contain .insight-label");
        assertFalse(css.contains(".insight-value"), "CSS should not contain .insight-value");
        // Generic grid utilities removed as they were only used by insights
        // Ensure core styles still exist
        assertTrue(css.contains(".period-grid"), "CSS should still contain period styles");
        assertTrue(css.contains(".account-grid"), "CSS should still contain account styles");
    }

    @Test
    void dashboardViewController_shouldNotHaveInsightsMethods() throws Exception {
        Class<?> clazz = Class.forName("com.budget.tracker.web.DashboardViewController");
        for (Method m : clazz.getDeclaredMethods()) {
            String name = m.getName().toLowerCase();
            assertFalse(name.contains("insight"), "Controller should not have insight methods, found: " + m.getName());
        }
    }

    @Test
    void dashboardViewController_sourceShouldNotReferenceInsights() throws Exception {
        String source = readFile("src/main/java/com/budget/tracker/web/DashboardViewController.java");
        assertFalse(source.contains("insightLabels"), "Controller source should not contain insightLabels");
        assertFalse(source.contains("insightCategories"), "Controller source should not contain insightCategories");
        assertFalse(source.contains("addInsightsAttributes"), "Controller source should not contain addInsightsAttributes");
        assertFalse(source.contains("toInsightRows"), "Controller source should not contain toInsightRows");
        // Should still contain recent and period logic
        assertTrue(source.contains("addRecentAttributes"), "Should still contain addRecentAttributes");
        assertTrue(source.contains("addPeriodAttributes"), "Should still contain addPeriodAttributes");
        assertTrue(source.contains("addAccountAttributes"), "Should still contain addAccountAttributes");
    }

    @Test
    void e2eTransactionSpec_shouldAssertInsightsRemoved() throws Exception {
        String e2e = readFile("e2e/tests/transactions.spec.ts");
        assertTrue(e2e.contains("should not display spending insights"), "E2E should have updated negative test");
        assertTrue(e2e.contains("insights-section"), "E2E should assert insights-section is hidden");
        assertFalse(e2e.contains("toBeVisible") && e2e.contains("Spending Insights") && e2e.contains("should display spending insights"),
                "Old positive E2E test should be removed");
    }
}
