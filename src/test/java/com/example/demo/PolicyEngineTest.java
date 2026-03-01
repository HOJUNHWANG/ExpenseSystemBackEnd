package com.example.demo;

import com.example.demo.domain.ExpenseItem;
import com.example.demo.domain.ExpenseReport;
import com.example.demo.service.PolicyEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for PolicyEngine — no Spring context, no mocks.
 */
class PolicyEngineTest {

    private ExpenseReport buildReport(String destination, LocalDate dep, LocalDate ret, List<ExpenseItem> items) {
        ExpenseReport r = new ExpenseReport();
        r.setDestination(destination);
        r.setDepartureDate(dep);
        r.setReturnDate(ret);
        items.forEach(it -> it.setExpenseReport(r));
        r.getItems().addAll(items);
        return r;
    }

    private ExpenseItem item(String category, BigDecimal amount, LocalDate date) {
        ExpenseItem it = new ExpenseItem();
        it.setCategory(category);
        it.setAmount(amount);
        it.setDate(date);
        it.setDescription(category + " expense");
        return it;
    }

    @Test
    void hotelAboveNightlyLimitIsWarned() {
        LocalDate day = LocalDate.now();
        ExpenseReport report = buildReport("New York, United States", day, day,
                List.of(item("Hotel", new BigDecimal("300.00"), day)));

        List<PolicyEngine.Warning> warnings = PolicyEngine.evaluateReportWarnings(report);

        assertThat(warnings).anyMatch(w -> w.getBaseCode().equals("HOTEL_ABOVE_CAP"));
    }

    @Test
    void airfareBelowUSLimitIsOk() {
        LocalDate day = LocalDate.now();
        ExpenseReport report = buildReport("Chicago, United States", day, day,
                List.of(item("Airfare", new BigDecimal("499.00"), day)));

        List<PolicyEngine.Warning> warnings = PolicyEngine.evaluateReportWarnings(report);

        assertThat(warnings).noneMatch(w -> w.getBaseCode().equals("AIRFARE_ABOVE_CAP"));
    }

    @Test
    void airfareAboveUSLimitIsWarned() {
        LocalDate day = LocalDate.now();
        ExpenseReport report = buildReport("Seattle, United States", day, day,
                List.of(item("Airfare", new BigDecimal("600.00"), day)));

        List<PolicyEngine.Warning> warnings = PolicyEngine.evaluateReportWarnings(report);

        assertThat(warnings).anyMatch(w -> w.getBaseCode().equals("AIRFARE_ABOVE_CAP"));
    }

    @Test
    void airfareAboveInternationalLimitIsWarned() {
        LocalDate day = LocalDate.now();
        ExpenseReport report = buildReport("London, United Kingdom", day, day,
                List.of(item("Airfare", new BigDecimal("1200.00"), day)));

        List<PolicyEngine.Warning> warnings = PolicyEngine.evaluateReportWarnings(report);

        assertThat(warnings).anyMatch(w -> w.getBaseCode().equals("AIRFARE_ABOVE_CAP"));
    }

    @Test
    void mealDailyAccumulationAboveLimitIsWarned() {
        LocalDate day = LocalDate.now();
        // Two meal items on same day totalling $95 > $75 limit
        ExpenseItem lunch = item("Meal", new BigDecimal("50.00"), day);
        ExpenseItem dinner = item("Meal", new BigDecimal("45.00"), day);
        ExpenseReport report = buildReport("New York, United States", day, day,
                List.of(lunch, dinner));

        List<PolicyEngine.Warning> warnings = PolicyEngine.evaluateReportWarnings(report);

        assertThat(warnings).anyMatch(w -> w.getBaseCode().equals("MEALS_ABOVE_DAILY_CAP"));
    }

    @Test
    void itemOutsideTripDateRangeIsWarned() {
        LocalDate dep = LocalDate.now().minusDays(3);
        LocalDate ret = LocalDate.now().minusDays(1);
        LocalDate outsideDate = LocalDate.now(); // after return

        ExpenseReport report = buildReport("Boston, United States", dep, ret,
                List.of(item("Travel", new BigDecimal("50.00"), outsideDate)));

        List<PolicyEngine.Warning> warnings = PolicyEngine.evaluateReportWarnings(report);

        assertThat(warnings).anyMatch(w -> w.getBaseCode().equals("ITEM_DATE_OUTSIDE_TRIP"));
    }

    @Test
    void departureDateAfterReturnDateIsWarned() {
        LocalDate dep = LocalDate.now();
        LocalDate ret = LocalDate.now().minusDays(1); // ret before dep

        ExpenseReport report = buildReport("Dallas, United States", dep, ret, List.of());

        List<PolicyEngine.Warning> warnings = PolicyEngine.evaluateReportWarnings(report);

        assertThat(warnings).anyMatch(w -> w.getBaseCode().equals("TRIP_DATES_INVALID"));
    }

    @Test
    void compliantReportHasNoWarnings() {
        LocalDate dep = LocalDate.now().minusDays(2);
        LocalDate ret = LocalDate.now().minusDays(1);

        ExpenseItem hotel = item("Hotel", new BigDecimal("200.00"), dep);   // under $250
        ExpenseItem airfare = item("Airfare", new BigDecimal("400.00"), dep); // under $500 US
        ExpenseItem meal = item("Meal", new BigDecimal("30.00"), dep);       // under $75 daily

        ExpenseReport report = buildReport("New York, United States", dep, ret,
                List.of(hotel, airfare, meal));

        List<PolicyEngine.Warning> warnings = PolicyEngine.evaluateReportWarnings(report);

        assertThat(warnings).isEmpty();
    }
}
