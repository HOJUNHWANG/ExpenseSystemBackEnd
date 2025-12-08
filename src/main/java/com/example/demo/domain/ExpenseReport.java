package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expense_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;              // Report Title

    private LocalDateTime createdAt;   // Creation Time

    private double totalAmount;           // Total Amount

    private String status;             // Status: DRAFT, SUBMITTED, APPROVED etc...

    @ManyToOne(fetch = FetchType.LAZY)
    private User submitter;            // Submitter (User)

    @OneToMany(mappedBy = "expenseReport",
            cascade = CascadeType.ALL,
            orphanRemoval = true)

    @Builder.Default

    private List<ExpenseItem> items = new ArrayList<>();
}
