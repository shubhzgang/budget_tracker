/**
 * NOTE: Use 'make test-int' to run integration tests and not gradle.
 */
package com.budget.tracker.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
public class ExpenditureSummaryIntegrationTest {

    /** Must match {@code TimeZones.APP_ZONE} — periods (today/week/month) are bucketed in this zone. */
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");

    /** A created expense: which app-zone day it lands on, its label (null = Unlabelled), amount, label display name. */
    private record Row(LocalDate date, String labelId, double amount, String labelName) {}

    private static final String BASE_URL = "http://localhost:8811/api/v1";
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private String token;
    private String accountId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "exp_test_" + UUID.randomUUID() + "@example.com";
        String password = "password123";

        // Register
        String signupJson = String.format("{\"email\":\"%s\", \"password\":\"%s\"}", email, password);
        HttpRequest registerRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(signupJson))
                .build();
        client.send(registerRequest, HttpResponse.BodyHandlers.ofString());

        // Login
        String loginJson = String.format("{\"email\":\"%s\", \"password\":\"%s\"}", email, password);
        HttpRequest loginRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginJson))
                .build();
        HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
        token = mapper.readTree(loginResponse.body()).get("token").asText();

        // Create an account
        String accountJson = "{\"name\":\"Main Account\", \"type\":\"CASH\", \"initialBalance\":0.00}";
        HttpRequest createAccountRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/accounts"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(accountJson))
                .build();
        HttpResponse<String> accountResponse = client.send(createAccountRequest, HttpResponse.BodyHandlers.ofString());
        accountId = mapper.readTree(accountResponse.body()).get("id").asText();
    }

    @Test
    void labelExpenditureBreakdownReflectsRenameAndDelete() throws Exception {
        // Create two labels
        String needsId = createLabel("NEEDS");
        String wantsId = createLabel("WANTS");

        // A labelled expense today
        createExpense(plain(25.00), needsId);
        // An unlabelled expense today
        createExpense(plain(15.00), null);

        // Summary should show per-label breakdown for today
        JsonNode summary = getSummary();
        JsonNode todayByLabel = summary.get("todayByLabel");
        assertEquals(2, todayByLabel.size(), "expected NEEDS + Unlabelled in breakdown");

        double needsAmount = amountFor(todayByLabel, "NEEDS");
        double unlabelledAmount = amountFor(todayByLabel, "Unlabelled");
        assertEquals(25.00, needsAmount, 0.01);
        assertEquals(15.00, unlabelledAmount, 0.01);

        // Rename NEEDS -> DESIRES; historical rows must be rewritten via recompute
        updateLabel(needsId, "DESIRES");
        JsonNode afterRename = getSummary().get("todayByLabel");
        assertEquals(2, afterRename.size());
        assertEquals(25.00, amountFor(afterRename, "DESIRES"), 0.01);
        assertNull(amountForNullable(afterRename, "NEEDS"), "renamed label should disappear");

        // Delete the label; its breakdown rows must be removed via recompute, and the
        // now-unlabelled transaction rolls into the Unlabelled bucket (25 + 15 = 40).
        deleteLabel(needsId);
        JsonNode afterDelete = getSummary().get("todayByLabel");
        assertNull(amountForNullable(afterDelete, "DESIRES"), "deleted label should disappear from breakdown");
        assertEquals(1, afterDelete.size());
        assertEquals(40.00, amountFor(afterDelete, "Unlabelled"), 0.01);
    }

    @Test
    void allSixPeriodTotalsAndLabelBreakdowns() throws Exception {
        LocalDate today = LocalDate.now(APP_ZONE);
        LocalDate thisMonday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        YearMonth thisMonth = YearMonth.from(today);
        YearMonth lastMonth = thisMonth.minusMonths(1);

        String a = createLabel("LBL_A");
        String b = createLabel("LBL_B");

        // One expense per anchor date, chosen to populate every bucket. Expectations below are
        // derived from date *membership* (not from which anchor was intended), so the overlaps
        // between buckets (today ⊂ thisWeek ⊂ thisMonth, last-week days landing in last month, …)
        // are handled correctly regardless of which weekday "today" is.
        List<Row> rows = List.of(
                new Row(today, a, 100.00, "LBL_A"),                 // today
                new Row(today, null, 7.00, null),                   // unlabelled, today
                new Row(today.minusDays(1), b, 101.00, "LBL_B"),    // yesterday
                new Row(thisMonday, a, 102.00, "LBL_A"),            // start of this week
                new Row(thisMonday.minusDays(1), b, 103.00, "LBL_B"), // last day of last week
                new Row(lastMonth.atDay(15), a, 104.00, "LBL_A"));  // mid last month
        for (Row r : rows) createExpenseOn(r.date(), plain(r.amount()), r.labelId());

        JsonNode summary = getSummary();
        assertPeriod(summary, "today", d -> d.equals(today), rows);
        assertPeriod(summary, "yesterday", d -> d.equals(today.minusDays(1)), rows);
        assertPeriod(summary, "thisWeek", d -> !d.isBefore(thisMonday) && d.isBefore(thisMonday.plusWeeks(1)), rows);
        assertPeriod(summary, "lastWeek", d -> !d.isBefore(thisMonday.minusWeeks(1)) && d.isBefore(thisMonday), rows);
        assertPeriod(summary, "thisMonth", d -> YearMonth.from(d).equals(thisMonth), rows);
        assertPeriod(summary, "lastMonth", d -> YearMonth.from(d).equals(lastMonth), rows);
    }

    /**
     * Asserts the overall total and the per-label breakdown for one period bucket against the subset
     * of {@code rows} whose date falls inside it (as defined by {@code inPeriod}).
     */
    private void assertPeriod(JsonNode summary, String period, Predicate<LocalDate> inPeriod, List<Row> rows) {
        double expectedTotal = 0;
        Map<String, Double> expectedByLabel = new HashMap<>();
        for (Row r : rows) {
            if (!inPeriod.test(r.date())) continue;
            expectedTotal += r.amount();
            expectedByLabel.merge(r.labelName() == null ? "Unlabelled" : r.labelName(), r.amount(), Double::sum);
        }

        assertEquals(expectedTotal, summary.get(period).asDouble(), 0.01, period + " total");

        JsonNode breakdown = summary.get(period + "ByLabel");
        assertTrue(breakdown.size() > 0, period + "ByLabel should not be empty");
        assertEquals(expectedByLabel.size(), breakdown.size(), period + "ByLabel entry count");
        for (Map.Entry<String, Double> e : expectedByLabel.entrySet()) {
            assertEquals(e.getValue(), amountFor(breakdown, e.getKey()), 0.01, period + " " + e.getKey());
        }
    }

    private JsonNode getSummary() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transactions/expenditure-summary"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        return mapper.readTree(resp.body());
    }

    private void createExpense(String amount, String labelId) throws Exception {
        createExpenseOn(LocalDate.now(APP_ZONE), amount, labelId);
    }

    /**
     * Creates an EXPENSE landing on {@code date} in the app zone. The instant is anchored at local
     * noon so the calendar-day bucketing can never be flipped by a timezone-offset edge; the previous
     * hard-coded date made this test a time-bomb that only passed on that specific day.
     */
    private void createExpenseOn(LocalDate date, String amount, String labelId) throws Exception {
        String iso = isoInstantAtNoon(date);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/transactions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(labelId == null
                        ? "{\"amount\":" + amount + ",\"transactionDate\":\"" + iso + "\",\"description\":\"exp\",\"type\":\"EXPENSE\",\"accountId\":\"" + accountId + "\"}"
                        : "{\"amount\":" + amount + ",\"transactionDate\":\"" + iso + "\",\"description\":\"exp\",\"type\":\"EXPENSE\",\"accountId\":\"" + accountId + "\",\"labelIds\":[\"" + labelId + "\"]}"))
                .build();
        assertEquals(200, client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode(), "expense create failed");
    }

    /** ISO-8601 UTC instant string for local noon on {@code date} in the app zone. */
    private String isoInstantAtNoon(LocalDate date) {
        return date.atTime(12, 0).atZone(APP_ZONE).toOffsetDateTime().toInstant().toString();
    }

    private String createLabel(String name) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/labels"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"" + name + "\"}"))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        return mapper.readTree(resp.body()).get("id").asText();
    }

    private void updateLabel(String id, String name) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/labels/" + id))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString("{\"name\":\"" + name + "\"}"))
                .build();
        assertEquals(200, client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private void deleteLabel(String id) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/labels/" + id))
                .header("Authorization", "Bearer " + token)
                .DELETE()
                .build();
        assertEquals(204, client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private double amountFor(JsonNode breakdown, String labelName) {
        Double value = amountForNullable(breakdown, labelName);
        assertNotNull(value, "expected label " + labelName + " in breakdown");
        return value;
    }

    private Double amountForNullable(JsonNode breakdown, String labelName) {
        for (JsonNode node : breakdown) {
            if (labelName.equals(node.get("labelName").asText())) {
                return node.get("amount").asDouble();
            }
        }
        return null;
    }

    private String plain(double value) {
        return java.math.BigDecimal.valueOf(value).toPlainString();
    }
}
