package com.example.demo.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ExpenseItemCreateRequest {
    private LocalDate date;
    private String description;
    private double amount;
    private String category;
}
