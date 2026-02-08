// src/main/java/com/example/demo/dto/ExpenseReportListItemResponse.java
package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ExpenseReportListItemResponse {

    private Long id;
    private String title;
    private double totalAmount;
    private String status;
    private String destination;
    private LocalDate departureDate;
    private LocalDate returnDate;

}
