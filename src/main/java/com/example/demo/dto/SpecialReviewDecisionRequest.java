package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialReviewDecisionRequest {
    @NotNull
    private Long reviewerId;

    @NotBlank
    private String reviewerRole;

    private String reviewerComment;

    @NotEmpty
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
