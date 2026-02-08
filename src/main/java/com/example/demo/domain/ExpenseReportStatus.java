package com.example.demo.domain;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

public enum ExpenseReportStatus {

    DRAFT,
    SUBMITTED,

    // Policy exception flow
    FINANCE_SPECIAL_REVIEW,
    CHANGES_REQUESTED,

    APPROVED,
    REJECTED;

}
