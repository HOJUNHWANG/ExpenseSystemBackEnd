package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expense_reports", indexes = {
        @Index(name = "idx_expense_reports_submitter_id", columnList = "submitter_id"),
        @Index(name = "idx_expense_reports_status", columnList = "status"),
        @Index(name = "idx_expense_reports_created_at", columnList = "createdAt")
})
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

    @Enumerated(EnumType.STRING)
    private ExpenseReportStatus status;             // Status: DRAFT, SUBMITTED, APPROVED etc...

    private String destination;

    private LocalDate departureDate;
    private LocalDate returnDate;

    private LocalDateTime approvedAt;

    private String approvalComment;

    // Per-diem fields
    private double perDiemAmount;      // Calculated per-diem total
    private double perDiemRate;        // Daily rate ($25 domestic, $50 international)
    private int perDiemDays;           // Number of days

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
