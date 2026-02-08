package com.example.demo.service;

import com.example.demo.domain.ExpenseItem;
import com.example.demo.domain.ExpenseReport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Very small demo policy engine to make the app feel more like a real corporate tool.
 *
 * NOTE: This is intentionally simplified.
 */
public class PolicyEngine {

    public static final double RECEIPT_THRESHOLD = 25.0;
    public static final double HOTEL_NIGHTLY_LIMIT = 300.0;
    public static final double MEAL_DAILY_LIMIT = 75.0;

    public static List<String> evaluateReport(ExpenseReport report) {
        List<String> flags = new ArrayList<>();
        if (report == null) return flags;

        // Trip date sanity check
        LocalDate dep = report.getDepartureDate();
        LocalDate ret = report.getReturnDate();

        if (dep != null && ret != null && dep.isAfter(ret)) {
            flags.add("Trip dates invalid (departure after return)");
        }

        // Items
        if (report.getItems() != null) {
            // Meal daily rollup
            // (sum all items with category containing "meal" on same date)
            var mealByDate = new java.util.HashMap<LocalDate, Double>();

            for (ExpenseItem it : report.getItems()) {
                if (it == null) continue;

                // Date outside trip
                if (dep != null && ret != null && it.getDate() != null) {
                    if (it.getDate().isBefore(dep) || it.getDate().isAfter(ret)) {
                        flags.add("Item date outside trip range");
                        break;
                    }
                }

                // Receipt required
                if (it.getAmount() >= RECEIPT_THRESHOLD) {
                    flags.add("Receipt required for expenses >= $" + (int) RECEIPT_THRESHOLD);
                    // keep going (dedupe later)
                }

                // Hotel cap (best-effort heuristic)
                if (it.getCategory() != null && it.getCategory().toLowerCase().contains("lodg")) {
                    if (it.getAmount() > HOTEL_NIGHTLY_LIMIT) {
                        flags.add("Hotel above nightly cap ($" + (int) HOTEL_NIGHTLY_LIMIT + ")");
                    }
                }

                // Meals daily limit (heuristic)
                boolean isMeal = false;
                if (it.getCategory() != null && it.getCategory().toLowerCase().contains("meal")) isMeal = true;
                if (it.getDescription() != null && it.getDescription().toLowerCase().contains("per diem")) isMeal = true;
                if (isMeal && it.getDate() != null) {
                    mealByDate.put(it.getDate(), mealByDate.getOrDefault(it.getDate(), 0.0) + it.getAmount());
                }
            }

            for (var e : mealByDate.entrySet()) {
                if (e.getValue() > MEAL_DAILY_LIMIT) {
                    flags.add("Meals exceed daily cap ($" + (int) MEAL_DAILY_LIMIT + ")");
                    break;
                }
            }
        }

        return dedupe(flags);
    }

    private static List<String> dedupe(List<String> flags) {
        if (flags == null || flags.isEmpty()) return List.of();
        var out = new ArrayList<String>();
        for (String f : flags) {
            if (f == null || f.isBlank()) continue;
            if (!out.contains(f)) out.add(f);
        }
        return out;
    }
}
