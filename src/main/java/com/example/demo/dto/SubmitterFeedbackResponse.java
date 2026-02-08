package com.example.demo.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitterFeedbackResponse {
    private String specialReviewStatus; // APPROVED/REJECTED/PENDING
    private LocalDateTime decidedAt;
    private String reviewerName;
    private String reviewerComment;
    private List<SpecialReviewItemResponse> items;
}
