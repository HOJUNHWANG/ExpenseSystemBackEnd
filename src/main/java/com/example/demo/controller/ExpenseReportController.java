package com.example.demo.controller;

import com.example.demo.dto.ExpenseReportCreateRequest;
import com.example.demo.dto.ExpenseReportListItemResponse;
import com.example.demo.dto.ExpenseReportResponse;
import com.example.demo.dto.ApprovalRequest;
import com.example.demo.service.ExpenseReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expense-reports")
@RequiredArgsConstructor
public class ExpenseReportController {

    private final ExpenseReportService expenseReportService;

    @PostMapping
    public ResponseEntity<Long> create(@RequestBody ExpenseReportCreateRequest request) {
        Long id = expenseReportService.createReport(request);
        return ResponseEntity.ok(id);
    }

    // ✅ 1) 목록 조회: /api/expense-reports?submitterId=1
    @GetMapping
    public ResponseEntity<List<ExpenseReportListItemResponse>> list(
            @RequestParam Long submitterId) {

        var result = expenseReportService.getReportsBySubmitter(submitterId);
        return ResponseEntity.ok(result);
    }

    // ✅ 2) 상세 조회: /api/expense-reports/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ExpenseReportResponse> getOne(@PathVariable Long id) {
        var result = expenseReportService.getReport(id);
        return ResponseEntity.ok(result);
    }

    // ✅ 승인 API
    // POST /api/expense-reports/{id}/approve
    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable Long id,
            @RequestBody ApprovalRequest request
    ) {
        expenseReportService.approveReport(id, request);
        return ResponseEntity.ok().build();
    }

    // ✅ 반려 API
    // POST /api/expense-reports/{id}/reject
    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable Long id,
            @RequestBody ApprovalRequest request
    ) {
        expenseReportService.rejectReport(id, request);
        return ResponseEntity.ok().build();
    }

}
