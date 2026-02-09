package com.example.demo.domain;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

public enum ExpenseReportStatus {

    DRAFT,

    // Legacy statuses (kept for backward compatibility with existing DB rows)
    // NOTE: new workflow should NOT write these.
    SUBMITTED,
    FINANCE_SPECIAL_REVIEW,

    // Normal approval chain
    MANAGER_REVIEW,
    CFO_REVIEW,
    CEO_REVIEW,

    // Policy exception flow (handled by CFO or CEO depending on submitter)
    CFO_SPECIAL_REVIEW,
    CEO_SPECIAL_REVIEW,
    CHANGES_REQUESTED,

    APPROVED,
    REJECTED;

}
