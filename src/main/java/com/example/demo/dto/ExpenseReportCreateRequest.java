package com.example.demo.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExpenseReportCreateRequest {

    private Long submitterId;  // Submitted User

    private String title;      // Report Title

    private List<ExpenseItemCreateRequest> items; // Item List

}
