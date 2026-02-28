package com.example.demo.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseItemCreateRequest {
    @NotNull
    private LocalDate date;

    @NotBlank
    @Size(max = 500)
    private String description;

    @NotNull
    @Positive(message = "Amount must be greater than 0")
    @DecimalMax(value = "999999.99", message = "Amount cannot exceed $999,999.99")
    private BigDecimal amount;

    @NotBlank
    private String category;
}
