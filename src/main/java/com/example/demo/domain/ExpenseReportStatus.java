package com.example.demo.domain;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

public enum ExpenseReportStatus {
    SUBMITTED,
    APPROVED,
    REJECTED;

    @Enumerated(EnumType.STRING)
    private ExpenseReportStatus status;

}
