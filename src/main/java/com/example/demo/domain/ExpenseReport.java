package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
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

    private String destination;

    private LocalDate departureDate;
    private LocalDate returnDate;

    private LocalDateTime approvedAt;

    private String approvalComment;

    @ManyToOne(fetch = FetchType.LAZY)
    private User submitter;            // Submitter (User)

    @ManyToOne(fetch = FetchType.LAZY)
    private User approver;

    @OneToMany(mappedBy = "expenseReport",
            cascade = CascadeType.ALL,
            orphanRemoval = true)

    @Builder.Default

    private List<ExpenseItem> items = new ArrayList<>();
}
