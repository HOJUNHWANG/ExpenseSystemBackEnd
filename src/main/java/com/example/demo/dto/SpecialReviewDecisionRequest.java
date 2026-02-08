package com.example.demo.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialReviewDecisionRequest {
    private Long reviewerId;
    private String reviewerRole;
    private String reviewerComment;
    private List<ItemDecision> decisions;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemDecision {
        public String code;
        public String decision; // APPROVE / REJECT
        public String financeReason;
    }
}
