package com.example.demo.controller;

import com.example.demo.domain.ExpenseReportStatus;
import com.example.demo.dto.ExpenseReportCreateRequest;
import com.example.demo.dto.ExpenseReportListItemResponse;
import com.example.demo.dto.ExpenseReportResponse;
import com.example.demo.dto.ApprovalRequest;
import com.example.demo.dto.PageResponse;
import com.example.demo.service.ExpenseReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Expense Reports", description = "CRUD and workflow operations for expense reports")
@RestController
@RequestMapping("/api/expense-reports")
@RequiredArgsConstructor
public class ExpenseReportController {

    private final ExpenseReportService expenseReportService;

    @Operation(summary = "Create a new expense report", description = "Creates a draft expense report with line items")
    @PostMapping
    public ResponseEntity<Long> create(@Valid @RequestBody ExpenseReportCreateRequest request) {
        Long id = expenseReportService.createReport(request);
        return ResponseEntity.ok(id);
    }

    @Operation(summary = "List reports by submitter", description = "Returns reports for a given submitter, optionally filtered by status. Supports pagination with page/size params.")
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam Long submitterId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size
    ) {
        if (page != null) {
            if (status != null && !status.isBlank()) {
                ExpenseReportStatus s = ExpenseReportStatus.valueOf(status.toUpperCase());
                return ResponseEntity.ok(expenseReportService.findBySubmitterAndStatusPaged(submitterId, s, page, size));
            }
            return ResponseEntity.ok(expenseReportService.getReportsBySubmitterPaged(submitterId, page, size));
        }
        if (status == null || status.isBlank()) {
            return ResponseEntity.ok(expenseReportService.getReportsBySubmitter(submitterId));
        } else {
            ExpenseReportStatus s = ExpenseReportStatus.valueOf(status.toUpperCase());
            return ResponseEntity.ok(
                    expenseReportService.findBySubmitterAndStatus(submitterId, s)
            );
        }
    }

    @Operation(summary = "List reports pending approval", description = "Returns reports awaiting the given role's approval. Supports pagination with page/size params.")
    @GetMapping("/pending-approval")
    public ResponseEntity<?> listPendingApproval(
            @RequestParam String requesterRole,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size
    ) {
        if (page != null) {
            return ResponseEntity.ok(expenseReportService.getReportsPendingApprovalPaged(requesterRole, page, size));
        }
        return ResponseEntity.ok(expenseReportService.getReportsPendingApproval(requesterRole));
    }

    @Operation(summary = "Submit a report for approval", description = "Routes the report into the approval chain or CFO exception review")
    @PostMapping("/{id}/submit")
    public ResponseEntity<String> submit(@PathVariable Long id, @Valid @RequestBody com.example.demo.dto.SubmitRequest req) {
        var st = expenseReportService.submitReport(id, req);
        return ResponseEntity.ok(st.name());
    }

    @Operation(summary = "Get exception review details", description = "CFO endpoint to view policy-exception review details")
    @GetMapping("/{id}/special-review")
    public ResponseEntity<com.example.demo.dto.SpecialReviewResponse> getExceptionReview(@PathVariable Long id) {
        return ResponseEntity.ok(expenseReportService.getExceptionReview(id));
    }

    @Operation(summary = "Get submitter feedback", description = "Returns feedback for the submitter after changes are requested")
    @GetMapping("/{id}/submitter-feedback")
    public ResponseEntity<com.example.demo.dto.SubmitterFeedbackResponse> submitterFeedback(
            @PathVariable Long id,
            @RequestParam Long requesterId
    ) {
        return ResponseEntity.ok(expenseReportService.getSubmitterFeedback(id, requesterId));
    }

    @Operation(summary = "Decide exception review", description = "CFO decides on each flagged line item (approve/reject)")
    @PostMapping("/{id}/special-review/decide")
    public ResponseEntity<String> decideExceptionReview(
            @PathVariable Long id,
            @Valid @RequestBody com.example.demo.dto.SpecialReviewDecisionRequest req
    ) {
        var st = expenseReportService.decideExceptionReview(id, req);
        return ResponseEntity.ok(st.name());
    }

    @Operation(summary = "Search reports", description = "Full-text search with filters. Employees see only their own reports; managers and above see all. Supports pagination with page/size params.")
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam Long requesterId,
            @RequestParam String requesterRole,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Double minTotal,
            @RequestParam(required = false) Double maxTotal,
            @RequestParam(required = false, defaultValue = "activity_desc") String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size
    ) {
        if (page != null) {
            return ResponseEntity.ok(expenseReportService.searchReportsPaged(requesterId, requesterRole, q, status, minTotal, maxTotal, sort, page, size));
        }
        return ResponseEntity.ok(expenseReportService.searchReports(requesterId, requesterRole, q, status, minTotal, maxTotal, sort));
    }

    @Operation(summary = "Get aggregate statistics", description = "Returns category breakdown, monthly trends, and approval rates for charts")
    @GetMapping("/stats")
    public ResponseEntity<com.example.demo.dto.StatsResponse> stats() {
        return ResponseEntity.ok(expenseReportService.getStats());
    }

    @Operation(summary = "Get recent activity", description = "Returns recently updated reports for the dashboard activity feed")
    @GetMapping("/activity")
    public ResponseEntity<List<com.example.demo.dto.ExpenseReportActivityItem>> activity(
            @RequestParam Long requesterId,
            @RequestParam String requesterRole,
            @RequestParam(required = false, defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(expenseReportService.getRecentActivity(requesterId, requesterRole, limit));
    }

    @Operation(summary = "Get report by ID", description = "Returns full expense report details including line items and approval history")
    @GetMapping("/{id}")
    public ResponseEntity<ExpenseReportResponse> getOne(@PathVariable Long id) {
        var result = expenseReportService.getReport(id);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Update a report", description = "Updates a draft or changes-requested report. Only the owner can update.")
    @PutMapping("/{id}")
    public ResponseEntity<String> update(
            @PathVariable Long id,
            @Valid @RequestBody com.example.demo.dto.ExpenseReportUpdateRequest req
    ) {
        var st = expenseReportService.updateReport(id, req);
        return ResponseEntity.ok(st.name());
    }

    @Operation(summary = "Delete a draft report", description = "Permanently deletes a report in DRAFT status")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            @RequestParam Long requesterId
    ) {
        expenseReportService.deleteDraft(id, requesterId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Approve a report", description = "Advances the report to the next approval stage or marks it as approved")
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

    @Operation(summary = "Reject a report", description = "Rejects the report and optionally requests changes from the submitter")
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
