package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "expense_items")
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

    private String description;    // Description

    private double amount;            // Amount

    private String category;       // Category of the expense

    @ManyToOne(fetch = FetchType.LAZY)
    private ExpenseReport expenseReport;  // Which report it included

}
