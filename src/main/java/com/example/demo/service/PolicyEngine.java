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

    // Demo corporate policy knobs
    // NOTE: We intentionally do NOT implement receipt attachment in this demo.
    public static final double HOTEL_NIGHTLY_LIMIT = 250.0;
    public static final double ENTERTAINMENT_LIMIT = 100.0;
    public static final double AIRFARE_LIMIT_US = 500.0;
    public static final double AIRFARE_LIMIT_INTL = 1000.0;
    public static final double MEAL_DAILY_LIMIT = 75.0;

    // Additional demo caps for remaining categories
    public static final double TRANSPORTATION_LIMIT = 150.0;
    public static final double OFFICE_LIMIT = 200.0;

    @lombok.Builder
    @lombok.Getter
    public static class Warning {
        /**
         * Warning key used for exception review decisions.
         *
         * NOTE: This must be unique per exception checklist row.
         * We encode per-item scope into the code (e.g. HOTEL_ABOVE_CAP#123).
         */
        private final String code;

        /**
         * Base policy code (stable, human readable) e.g. HOTEL_ABOVE_CAP.
         */
        private final String baseCode;

        private final String message;

        /**
         * Optional item scope (null for report-level warnings).
         */
        private final Long itemId;
    }

    public static List<Warning> evaluateReportWarnings(ExpenseReport report) {
        List<Warning> flags = new ArrayList<>();
        if (report == null) return flags;

        // Trip date sanity check
        LocalDate dep = report.getDepartureDate();
        LocalDate ret = report.getReturnDate();

        if (dep != null && ret != null && dep.isAfter(ret)) {
            flags.add(Warning.builder()
                    .code("TRIP_DATES_INVALID")
                    .baseCode("TRIP_DATES_INVALID")
                    .message("Trip dates invalid (departure after return)")
                    .itemId(null)
                    .build());
        }

        // Items
        if (report.getItems() != null) {
            // Meal daily rollup
            // (sum all items with category containing "meal" on same date)
            var mealByDate = new java.util.HashMap<LocalDate, Double>();

            for (ExpenseItem it : report.getItems()) {
                if (it == null) continue;
                Long itemId = it.getId();

                // Date outside trip
                if (dep != null && ret != null && it.getDate() != null) {
                    if (it.getDate().isBefore(dep) || it.getDate().isAfter(ret)) {
                        flags.add(Warning.builder()
                                .code("ITEM_DATE_OUTSIDE_TRIP")
                                .baseCode("ITEM_DATE_OUTSIDE_TRIP")
                                .message("Item date outside trip range")
                                .itemId(it.getId())
                                .build());
                        // add just once (report-level), but still scoped to an example item when possible
                        break;
                    }
                }

                // Entertainment cap
                if (it.getCategory() != null && it.getCategory().equalsIgnoreCase("Entertainment")) {
                    if (it.getAmount() > ENTERTAINMENT_LIMIT) {
                        String base = "ENTERTAINMENT_ABOVE_CAP";
                        flags.add(Warning.builder()
                                .code(itemId != null ? (base + "#" + itemId) : base)
                                .baseCode(base)
                                .message("Entertainment above cap ($" + (int) ENTERTAINMENT_LIMIT + ")")
                                .itemId(itemId)
                                .build());
                    }
                }

                // Hotel cap
                if (it.getCategory() != null && (it.getCategory().equalsIgnoreCase("Hotel") || it.getCategory().toLowerCase().contains("lodg"))) {
                    if (it.getAmount() > HOTEL_NIGHTLY_LIMIT) {
                        String base = "HOTEL_ABOVE_CAP";
                        flags.add(Warning.builder()
                                .code(itemId != null ? (base + "#" + itemId) : base)
                                .baseCode(base)
                                .message("Hotel above nightly cap ($" + (int) HOTEL_NIGHTLY_LIMIT + ")")
                                .itemId(itemId)
                                .build());
                    }
                }

                // Airfare cap (depends on destination country)
                if (it.getCategory() != null && it.getCategory().equalsIgnoreCase("Airfare")) {
                    boolean isUsTrip = isUnitedStatesTrip(report);
                    double limit = isUsTrip ? AIRFARE_LIMIT_US : AIRFARE_LIMIT_INTL;
                    if (it.getAmount() > limit) {
                        String base = "AIRFARE_ABOVE_CAP";
                        flags.add(Warning.builder()
                                .code(itemId != null ? (base + "#" + itemId) : base)
                                .baseCode(base)
                                .message("Airfare above cap ($" + (int) limit + ")")
                                .itemId(itemId)
                                .build());
                    }
                }

                // Transportation cap
                if (it.getCategory() != null && it.getCategory().equalsIgnoreCase("Transportation")) {
                    if (it.getAmount() > TRANSPORTATION_LIMIT) {
                        String base = "TRANSPORTATION_ABOVE_CAP";
                        flags.add(Warning.builder()
                                .code(itemId != null ? (base + "#" + itemId) : base)
                                .baseCode(base)
                                .message("Transportation above cap ($" + (int) TRANSPORTATION_LIMIT + ")")
                                .itemId(itemId)
                                .build());
                    }
                }

                // Office cap
                if (it.getCategory() != null && it.getCategory().equalsIgnoreCase("Office")) {
                    if (it.getAmount() > OFFICE_LIMIT) {
                        String base = "OFFICE_ABOVE_CAP";
                        flags.add(Warning.builder()
                                .code(itemId != null ? (base + "#" + itemId) : base)
                                .baseCode(base)
                                .message("Office expenses above cap ($" + (int) OFFICE_LIMIT + ")")
                                .itemId(itemId)
                                .build());
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
                    String base = "MEALS_ABOVE_DAILY_CAP";
                    LocalDate overDate = e.getKey();

                    // Scoped by date so multiple days can each produce a checklist row.
                    flags.add(Warning.builder()
                            .code(base + "#" + overDate)
                            .baseCode(base)
                            .message("Meals exceed daily cap ($" + (int) MEAL_DAILY_LIMIT + ")")
                            .itemId(null)
                            .build());
                }
            }
        }

        return flags;
    }

    // Backward compatible helper used by existing DTO code
    public static List<String> evaluateReport(ExpenseReport report) {
        return evaluateReportWarnings(report).stream().map(Warning::getMessage).toList();
    }

    private static boolean isUnitedStatesTrip(ExpenseReport report) {
        if (report == null) return false;
        String dest = report.getDestination();
        if (dest == null) return false;
        // Destination is stored as "City, Country" in this demo.
        String[] parts = dest.split(",");
        String country = parts.length >= 2 ? parts[parts.length - 1].trim() : dest.trim();
        return country.equalsIgnoreCase("United States")
                || country.equalsIgnoreCase("USA")
                || country.equalsIgnoreCase("United States of America");
    }

}


