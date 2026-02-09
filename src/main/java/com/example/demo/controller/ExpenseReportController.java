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

    // 🔹 승인 대기중 목록 (role-based)
    // MANAGER -> MANAGER_REVIEW
    // CFO     -> CFO_REVIEW
    // CEO     -> CEO_REVIEW
    @GetMapping("/pending-approval")
    public ResponseEntity<List<ExpenseReportListItemResponse>> listPendingApproval(
            @RequestParam String requesterRole
    ) {
        return ResponseEntity.ok(expenseReportService.getReportsPendingApproval(requesterRole));
    }

    // Submit (routes to approval chain or CFO_SPECIAL_REVIEW)
    @PostMapping("/{id}/submit")
    public ResponseEntity<String> submit(@PathVariable Long id, @RequestBody com.example.demo.dto.SubmitRequest req) {
        var st = expenseReportService.submitReport(id, req);
        return ResponseEntity.ok(st.name());
    }

    // Finance: view special review details
    @GetMapping("/{id}/special-review")
    public ResponseEntity<com.example.demo.dto.SpecialReviewResponse> getSpecialReview(@PathVariable Long id) {
        return ResponseEntity.ok(expenseReportService.getSpecialReview(id));
    }

    // Submitter: view feedback (after CHANGES_REQUESTED)
    @GetMapping("/{id}/submitter-feedback")
    public ResponseEntity<com.example.demo.dto.SubmitterFeedbackResponse> submitterFeedback(
            @PathVariable Long id,
            @RequestParam Long requesterId
    ) {
        return ResponseEntity.ok(expenseReportService.getSubmitterFeedback(id, requesterId));
    }

    // Finance: decide special review (approve/reject per item)
    @PostMapping("/{id}/special-review/decide")
    public ResponseEntity<String> decideSpecialReview(
            @PathVariable Long id,
            @RequestBody com.example.demo.dto.SpecialReviewDecisionRequest req
    ) {
        var st = expenseReportService.decideSpecialReview(id, req);
        return ResponseEntity.ok(st.name());
    }

    /**
     * Demo-friendly search.
     *
     * - EMPLOYEE: can only search their own reports
     * - MANAGER/FINANCE: can search all reports
     */
    @GetMapping("/search")
    public ResponseEntity<List<ExpenseReportListItemResponse>> search(
            @RequestParam Long requesterId,
            @RequestParam String requesterRole,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Double minTotal,
            @RequestParam(required = false) Double maxTotal,
            @RequestParam(required = false, defaultValue = "activity_desc") String sort
    ) {
        return ResponseEntity.ok(expenseReportService.searchReports(requesterId, requesterRole, q, status, minTotal, maxTotal, sort));
    }

    @GetMapping("/activity")
    public ResponseEntity<List<com.example.demo.dto.ExpenseReportActivityItem>> activity(
            @RequestParam Long requesterId,
            @RequestParam String requesterRole,
            @RequestParam(required = false, defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(expenseReportService.getRecentActivity(requesterId, requesterRole, limit));
    }

    // ✅ 2) 상세 조회: /api/expense-reports/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ExpenseReportResponse> getOne(@PathVariable Long id) {
        var result = expenseReportService.getReport(id);
        return ResponseEntity.ok(result);
    }

    /**
     * Update a report (demo): only allowed for owner when status is DRAFT or CHANGES_REQUESTED.
     */
    @PutMapping("/{id}")
    public ResponseEntity<String> update(
            @PathVariable Long id,
            @RequestBody com.example.demo.dto.ExpenseReportUpdateRequest req
    ) {
        var st = expenseReportService.updateReport(id, req);
        return ResponseEntity.ok(st.name());
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
