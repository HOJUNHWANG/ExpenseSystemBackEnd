package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class ExpenseItemResponse {

    private Long id;
    private LocalDate date;
    private String description;
    private BigDecimal amount;
    private String category;

}
