package com.example.demo.controller;

import com.example.demo.domain.ExpenseReportStatus;
import com.example.demo.dto.ExpenseReportCreateRequest;
import com.example.demo.dto.ExpenseReportListItemResponse;
import com.example.demo.dto.ExpenseReportResponse;
import com.example.demo.dto.ApprovalRequest;
import com.example.demo.service.ExpenseReportService;
import jakarta.validation.Valid;
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
    public ResponseEntity<Long> create(@Valid @RequestBody ExpenseReportCreateRequest request) {
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

    @GetMapping("/pending-approval")
    public ResponseEntity<List<ExpenseReportListItemResponse>> listPendingApproval(
            @RequestParam String requesterRole
    ) {
        return ResponseEntity.ok(expenseReportService.getReportsPendingApproval(requesterRole));
    }

    // Submit (routes to approval chain or CFO_SPECIAL_REVIEW)
    @PostMapping("/{id}/submit")
    public ResponseEntity<String> submit(@PathVariable Long id, @Valid @RequestBody com.example.demo.dto.SubmitRequest req) {
        var st = expenseReportService.submitReport(id, req);
        return ResponseEntity.ok(st.name());
    }

    // CFO: view exception review details (API path kept as /special-review for backward compatibility)
    @GetMapping("/{id}/special-review")
    public ResponseEntity<com.example.demo.dto.SpecialReviewResponse> getExceptionReview(@PathVariable Long id) {
        return ResponseEntity.ok(expenseReportService.getExceptionReview(id));
    }

    // Submitter: view feedback (after CHANGES_REQUESTED)
    @GetMapping("/{id}/submitter-feedback")
    public ResponseEntity<com.example.demo.dto.SubmitterFeedbackResponse> submitterFeedback(
            @PathVariable Long id,
            @RequestParam Long requesterId
    ) {
        return ResponseEntity.ok(expenseReportService.getSubmitterFeedback(id, requesterId));
    }

    // CFO: decide exception review (approve/reject per item) (API path kept as /special-review)
    @PostMapping("/{id}/special-review/decide")
    public ResponseEntity<String> decideExceptionReview(
            @PathVariable Long id,
            @Valid @RequestBody com.example.demo.dto.SpecialReviewDecisionRequest req
    ) {
        var st = expenseReportService.decideExceptionReview(id, req);
        return ResponseEntity.ok(st.name());
    }

    /**
     * Demo-friendly search.
     *
     * - EMPLOYEE: can only search their own reports
     * - MANAGER/CFO/CEO: can search all reports
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
            @Valid @RequestBody com.example.demo.dto.ExpenseReportUpdateRequest req
    ) {
        var st = expenseReportService.updateReport(id, req);
        return ResponseEntity.ok(st.name());
    }

    // Delete draft
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            @RequestParam Long requesterId
    ) {
        expenseReportService.deleteDraft(id, requesterId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalRequest request
    ) {
        try {
            expenseReportService.approveReport(id, request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalRequest request
    ) {
        try {
            expenseReportService.rejectReport(id, request);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
