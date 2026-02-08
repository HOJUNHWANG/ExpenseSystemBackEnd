package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "special_review_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialReviewItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private SpecialReview review;

    private String code;    // stable code for UI highlight
    private String message; // human readable

    @Column(length = 2000)
    private String employeeReason;

    @Enumerated(EnumType.STRING)
    private SpecialReviewDecision financeDecision; // APPROVE/REJECT

    @Column(length = 2000)
    private String financeReason;
}
