package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expense_reports", indexes = {
        @Index(name = "idx_expense_reports_submitter_id", columnList = "submitter_id"),
        @Index(name = "idx_expense_reports_status", columnList = "status"),
        @Index(name = "idx_expense_reports_created_at", columnList = "created_at")
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

    @Column(precision = 12, scale = 2)
    private BigDecimal totalAmount;           // Total Amount

    @Enumerated(EnumType.STRING)
    private ExpenseReportStatus status;             // Status: DRAFT, SUBMITTED, APPROVED etc...

    private String destination;

    private LocalDate departureDate;
    private LocalDate returnDate;

    private LocalDateTime approvedAt;   // Set only on actual APPROVAL

    private LocalDateTime rejectedAt;   // Set only on REJECTION

    @Column(length = 2000)
    private String approvalComment;

    // Per-diem fields
    @Column(precision = 12, scale = 2)
    private BigDecimal perDiemAmount;      // Calculated per-diem total

    @Column(precision = 5, scale = 2)
    private BigDecimal perDiemRate;        // Daily rate ($25 domestic, $50 international)

    private Integer perDiemDays;        // Number of days (nullable for schema migration compat)

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
