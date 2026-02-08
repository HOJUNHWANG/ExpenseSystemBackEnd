package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "special_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    private ExpenseReport report;

    @Enumerated(EnumType.STRING)
    private SpecialReviewStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private User reviewer; // typically FINANCE

    private String reviewerComment; // global comment on approve/reject

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SpecialReviewItem> items = new ArrayList<>();
}
