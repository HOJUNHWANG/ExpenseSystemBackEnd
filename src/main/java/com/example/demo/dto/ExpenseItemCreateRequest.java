package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ExpenseItemCreateRequest {
    @NotNull
    private LocalDate date;

    @NotBlank
    private String description;

    @PositiveOrZero
    private double amount;

    @NotBlank
    private String category;
}
