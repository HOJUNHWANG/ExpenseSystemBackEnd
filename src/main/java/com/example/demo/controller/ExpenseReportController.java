package com.example.demo.controller;

import com.example.demo.domain.ExpenseReportStatus;
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

    // ExpenseReportController.java
    @GetMapping
    public ResponseEntity<List<ExpenseReportListItemResponse>> list(
            @RequestParam Long submitterId,
            @RequestParam(required = false) String status
    ) {
        if (status == null || status.isBlank()) {
            return ResponseEntity.ok(expenseReportService.getReportsBySubmitter(submitterId));
        } else {
            ExpenseReportStatus s = ExpenseReportStatus.valueOf(status.toUpperCase());
            return ResponseEntity.ok(
                    expenseReportService.findBySubmitterAndStatus(submitterId, s)
            );
        }
    }

    // 🔹 새로 추가: 승인 대기중 목록 (Manager용)
    @GetMapping("/pending-approval")
    public ResponseEntity<List<ExpenseReportListItemResponse>> listPendingApproval() {
        return ResponseEntity.ok(expenseReportService.getReportsPendingApproval());
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
    public ResponseEntity<?> approve(
            @PathVariable Long id,
            @RequestBody ApprovalRequest request
    ) {
        try {
            expenseReportService.approveReport(id, request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 400 Bad Request + 에러 메시지 문자열
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ✅ 반려 API
    // POST /api/expense-reports/{id}/reject
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(
            @PathVariable Long id,
            @RequestBody ApprovalRequest request
    ) {
        try {
            expenseReportService.rejectReport(id, request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
