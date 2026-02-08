package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ExpenseItemResponse {

    private Long id;
    private LocalDate date;
    private String description;
    private double amount;
    private String category;

}
