package com.example.demo.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialReviewResponse {
    private Long id;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;
    private Long reviewerId;
    private String reviewerName;
    private String reviewerComment;
    private List<SpecialReviewItemResponse> items;
}
