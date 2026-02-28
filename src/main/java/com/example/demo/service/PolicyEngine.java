package com.example.demo.service;

import com.example.demo.domain.ExpenseItem;
import com.example.demo.domain.ExpenseReport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Very small demo policy engine to make the app feel more like a real corporate tool.
 *
 * NOTE: This is intentionally simplified.
 */
public class PolicyEngine {

    // Demo corporate policy knobs
    // NOTE: We intentionally do NOT implement receipt attachment in this demo.
    public static final BigDecimal HOTEL_NIGHTLY_LIMIT = new BigDecimal("250.00");
    public static final BigDecimal ENTERTAINMENT_LIMIT = new BigDecimal("100.00");
    public static final BigDecimal AIRFARE_LIMIT_US = new BigDecimal("500.00");
    public static final BigDecimal AIRFARE_LIMIT_INTL = new BigDecimal("1000.00");
    public static final BigDecimal MEAL_DAILY_LIMIT = new BigDecimal("75.00");

    // Additional demo caps for remaining categories
    public static final BigDecimal TRANSPORTATION_LIMIT = new BigDecimal("150.00");
    public static final BigDecimal OFFICE_LIMIT = new BigDecimal("200.00");

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
            Map<LocalDate, BigDecimal> mealByDate = new HashMap<>();

            for (ExpenseItem it : report.getItems()) {
                if (it == null) continue;
                Long itemId = it.getId();
                BigDecimal amount = it.getAmount() != null ? it.getAmount() : BigDecimal.ZERO;

                // Date outside trip
                if (dep != null && ret != null && it.getDate() != null) {
                    if (it.getDate().isBefore(dep) || it.getDate().isAfter(ret)) {
                        flags.add(Warning.builder()
                                .code("ITEM_DATE_OUTSIDE_TRIP")
                                .baseCode("ITEM_DATE_OUTSIDE_TRIP")
                                .message("Item date outside trip range")
                                .itemId(it.getId())
                                .build());
                        break;
                    }
                }

                // Entertainment cap
                if (it.getCategory() != null && it.getCategory().equalsIgnoreCase("Entertainment")) {
                    if (amount.compareTo(ENTERTAINMENT_LIMIT) > 0) {
                        String base = "ENTERTAINMENT_ABOVE_CAP";
                        flags.add(Warning.builder()
                                .code(itemId != null ? (base + "#" + itemId) : base)
                                .baseCode(base)
                                .message("Entertainment above cap ($" + ENTERTAINMENT_LIMIT.intValue() + ")")
                                .itemId(itemId)
                                .build());
                    }
                }

                // Hotel cap
                if (it.getCategory() != null && (it.getCategory().equalsIgnoreCase("Hotel") || it.getCategory().toLowerCase().contains("lodg"))) {
                    if (amount.compareTo(HOTEL_NIGHTLY_LIMIT) > 0) {
                        String base = "HOTEL_ABOVE_CAP";
                        flags.add(Warning.builder()
                                .code(itemId != null ? (base + "#" + itemId) : base)
                                .baseCode(base)
                                .message("Hotel above nightly cap ($" + HOTEL_NIGHTLY_LIMIT.intValue() + ")")
                                .itemId(itemId)
                                .build());
                    }
                }

                // Airfare cap (depends on destination country)
                if (it.getCategory() != null && it.getCategory().equalsIgnoreCase("Airfare")) {
                    boolean isUsTrip = isUnitedStatesTrip(report);
                    BigDecimal limit = isUsTrip ? AIRFARE_LIMIT_US : AIRFARE_LIMIT_INTL;
                    if (amount.compareTo(limit) > 0) {
                        String base = "AIRFARE_ABOVE_CAP";
                        flags.add(Warning.builder()
                                .code(itemId != null ? (base + "#" + itemId) : base)
                                .baseCode(base)
                                .message("Airfare above cap ($" + limit.intValue() + ")")
                                .itemId(itemId)
                                .build());
                    }
                }

                // Transportation cap
                if (it.getCategory() != null && it.getCategory().equalsIgnoreCase("Transportation")) {
                    if (amount.compareTo(TRANSPORTATION_LIMIT) > 0) {
                        String base = "TRANSPORTATION_ABOVE_CAP";
                        flags.add(Warning.builder()
                                .code(itemId != null ? (base + "#" + itemId) : base)
                                .baseCode(base)
                                .message("Transportation above cap ($" + TRANSPORTATION_LIMIT.intValue() + ")")
                                .itemId(itemId)
                                .build());
                    }
                }

                // Office cap
                if (it.getCategory() != null && it.getCategory().equalsIgnoreCase("Office")) {
                    if (amount.compareTo(OFFICE_LIMIT) > 0) {
                        String base = "OFFICE_ABOVE_CAP";
                        flags.add(Warning.builder()
                                .code(itemId != null ? (base + "#" + itemId) : base)
                                .baseCode(base)
                                .message("Office expenses above cap ($" + OFFICE_LIMIT.intValue() + ")")
                                .itemId(itemId)
                                .build());
                    }
                }

                // Meals daily limit (heuristic)
                boolean isMeal = false;
                if (it.getCategory() != null && it.getCategory().toLowerCase().contains("meal")) isMeal = true;
                if (it.getDescription() != null && it.getDescription().toLowerCase().contains("per diem")) isMeal = true;
                if (isMeal && it.getDate() != null) {
                    mealByDate.merge(it.getDate(), amount, BigDecimal::add);
                }
            }

            for (Map.Entry<LocalDate, BigDecimal> e : mealByDate.entrySet()) {
                if (e.getValue().compareTo(MEAL_DAILY_LIMIT) > 0) {
                    String base = "MEALS_ABOVE_DAILY_CAP";
                    LocalDate overDate = e.getKey();

                    flags.add(Warning.builder()
                            .code(base + "#" + overDate)
                            .baseCode(base)
                            .message("Meals exceed daily cap ($" + MEAL_DAILY_LIMIT.intValue() + ")")
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
