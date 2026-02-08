package com.example.demo.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseReportActivityItem {
    private Long id;
    private String title;
    private String status;
    private Double totalAmount;

    private Long submitterId;
    private String submitterName;

    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime lastActivityAt;
    private String activityLabel;

    private boolean flagged;
}
