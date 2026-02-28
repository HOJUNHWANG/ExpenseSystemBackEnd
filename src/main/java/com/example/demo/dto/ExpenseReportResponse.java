// src/main/java/com/example/demo/dto/ExpenseReportResponse.java
package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExpenseReportResponse {

    private Long id;
    private String title;
    private BigDecimal totalAmount;
    private String status;

    private String destination;
    private LocalDate departureDate;
    private LocalDate returnDate;

    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;   // Set only on APPROVAL
    private LocalDateTime rejectedAt;   // Set only on REJECTION

    private Long submitterId;
    private String submitterName;

    private Long approverId;
    private String approverName;

    private String approvalComment;

    // Per-diem
    private BigDecimal perDiemAmount;
    private BigDecimal perDiemRate;
    private int perDiemDays;

    // Demo policy flags
    private boolean flagged;
    private List<String> policyFlags;

    // Code-based warnings for submit UX
    private List<PolicyWarningResponse> policyWarnings;

    private List<ExpenseItemResponse> items;
}
