package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expense_items", indexes = {
        @Index(name = "idx_expense_items_report_id", columnList = "expense_report_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate date;        // Expense Date

    @Column(length = 500)
    private String description;    // Description

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;        // Amount

    private String category;       // Category of the expense

    @ManyToOne(fetch = FetchType.LAZY)
    private ExpenseReport expenseReport;  // Which report it included

}
