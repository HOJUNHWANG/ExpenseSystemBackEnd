package com.example.demo.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialReviewItemResponse {
    private Long id;
    private String code;
    private String message;
    private String employeeReason;
    private String financeDecision;
    private String financeReason;
}
