// src/main/java/com/example/demo/dto/ExpenseReportResponse.java
package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExpenseReportResponse {

    private Long id;
    private String title;
    private double totalAmount;
    private String status;

    private String destination;
    private LocalDate departureDate;
    private LocalDate returnDate;

    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;

    private Long submitterId;
    private String submitterName;

    private Long approverId;
    private String approverName;

    private List<ExpenseItemResponse> items;
}
