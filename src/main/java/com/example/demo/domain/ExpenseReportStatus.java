package com.example.demo.domain;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

public enum ExpenseReportStatus {

    SUBMITTED,
    APPROVED,
    REJECTED;

}
