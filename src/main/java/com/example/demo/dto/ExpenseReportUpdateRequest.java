package com.example.demo.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseReportUpdateRequest {
    private Long submitterId;

    private String title;
    private String destination;
    private LocalDate departureDate;
    private LocalDate returnDate;

    private List<ExpenseItemCreateRequest> items;
}
