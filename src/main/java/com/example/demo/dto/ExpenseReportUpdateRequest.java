package com.example.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseReportUpdateRequest {
    @NotNull
    private Long submitterId;

    @NotBlank
    private String title;
    private String destination;
    private LocalDate departureDate;
    private LocalDate returnDate;

    @NotEmpty
    @Valid
    private List<ExpenseItemCreateRequest> items;
}
