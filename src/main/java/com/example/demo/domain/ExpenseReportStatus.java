package com.example.demo.domain;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

public enum ExpenseReportStatus {

    DRAFT,

    // Normal approval chain
    MANAGER_REVIEW,
    CFO_REVIEW,
    CEO_REVIEW,

    // Policy exception flow (handled by CFO)
    CFO_SPECIAL_REVIEW,
    CHANGES_REQUESTED,

    APPROVED,
    REJECTED;

}
