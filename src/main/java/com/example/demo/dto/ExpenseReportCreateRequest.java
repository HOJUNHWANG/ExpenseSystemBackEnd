package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ExpenseReportCreateRequest {

    @NotNull
    private Long submitterId;  // Submitted User

    @NotBlank
    private String title;      // Report Title

    @NotEmpty
    @Valid
    private List<ExpenseItemCreateRequest> items; // Item List

}
