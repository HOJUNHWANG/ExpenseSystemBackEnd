package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsResponse {
    private long totalReports;
    private long approved;
    private long rejected;
    private long pending;
    private double totalAmount;
    private List<CategoryStat> byCategory;
    private List<MonthStat> byMonth;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStat {
        private String category;
        private double amount;
        private int count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthStat {
        private String month; // "2025-01"
        private double amount;
        private int count;
    }
}
