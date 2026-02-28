// src/main/java/com/example/demo/dto/ExpenseReportListItemResponse.java
package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class ExpenseReportListItemResponse {

    private Long id;
    private String title;
    private BigDecimal totalAmount;
    private String status;
    private String destination;
    private LocalDate departureDate;
    private LocalDate returnDate;

    // Per-diem
    private BigDecimal perDiemAmount;
    private BigDecimal perDiemRate;
    private int perDiemDays;

    // Demo policy flags (for list/search UI)
    private boolean flagged;

}
