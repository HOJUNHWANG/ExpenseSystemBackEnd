package com.example.demo.repository;

import com.example.demo.domain.ExpenseReport;
import com.example.demo.domain.ExpenseReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseReportRepository extends JpaRepository<ExpenseReport, Long> {

    // For searching the report that submitted by certain User
    List<ExpenseReport> findBySubmitterId(Long submitterId);
    List<ExpenseReport> findBySubmitterIdAndStatus(Long submitterId, ExpenseReportStatus status);
}
