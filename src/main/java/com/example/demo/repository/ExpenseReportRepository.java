package com.example.demo.repository;

import com.example.demo.domain.ExpenseReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseReportRepository extends JpaRepository<ExpenseReport, Long> {

    // For searching the report that submitted by certain User
    List<ExpenseReport> findBySubmitterId(Long submitterId);

}
