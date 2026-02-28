package com.example.demo.dto;

import lombok.*;

import java.math.BigDecimal;
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
    private BigDecimal totalAmount;

    private Long submitterId;
    private String submitterName;

    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime lastActivityAt;
    private String activityLabel;

    private boolean flagged;
}
