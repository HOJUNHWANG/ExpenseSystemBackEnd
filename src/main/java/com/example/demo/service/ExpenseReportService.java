package com.example.demo.service;

import com.example.demo.domain.*;
import com.example.demo.dto.ExpenseItemResponse;
import com.example.demo.dto.ExpenseReportCreateRequest;
import com.example.demo.dto.ExpenseReportListItemResponse;
import com.example.demo.dto.ExpenseReportResponse;
import com.example.demo.dto.ApprovalRequest;
import com.example.demo.repository.ExpenseReportRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseReportService {

    private final ExpenseReportRepository expenseReportRepository;
    private final com.example.demo.repository.SpecialReviewRepository specialReviewRepository;
    private final UserRepository userRepository;

    public Long createReport(ExpenseReportCreateRequest request) {

        // 1) submitterId로 User 찾아오기
        User submitter = userRepository.findById(request.getSubmitterId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.getSubmitterId()));

        // 2) 보고서 객체 생성
        ExpenseReport report = ExpenseReport.builder()
                .title(request.getTitle())
                .createdAt(LocalDateTime.now())
                .submitter(submitter)
                .build();

        report.setStatus(ExpenseReportStatus.DRAFT);

        // @Builder.Default 덕분에 items 리스트는 이미 new ArrayList<>() 상태라고 가정
        double total = 0;

        // 3) 각 item DTO → ExpenseItem 엔티티로 변환해서 report에 추가
        if (request.getItems() != null) {
            for (var itemReq : request.getItems()) {
                ExpenseItem item = ExpenseItem.builder()
                        .date(itemReq.getDate())
                        .description(itemReq.getDescription())
                        .amount(itemReq.getAmount())
                        .category(itemReq.getCategory())
                        .build();

                // 양방향 관계 세팅
                item.setExpenseReport(report);
                report.getItems().add(item);

                total += itemReq.getAmount();
            }
        }

        report.setTotalAmount(total);

        // 4) 저장 (cascade = ALL 덕분에 item들도 같이 저장됨)
        ExpenseReport saved = expenseReportRepository.save(report);

        return saved.getId();
    }

    // ✅ 1) 특정 사용자의 보고서 목록
    public List<ExpenseReportListItemResponse> getReportsBySubmitter (Long submitterId){
        List<ExpenseReport> reports =
                expenseReportRepository.findBySubmitterId(submitterId);

        return reports.stream()
                .map(r -> {
                    boolean flagged = !PolicyEngine.evaluateReport(r).isEmpty();
                    return ExpenseReportListItemResponse.builder()
                            .id(r.getId())
                            .title(r.getTitle())
                            .totalAmount(r.getTotalAmount())
                            .status(r.getStatus().name())
                            .destination(r.getDestination())
                            .departureDate(r.getDepartureDate())
                            .returnDate(r.getReturnDate())
                            .flagged(flagged)
                            .build();
                })
                .toList();
    }

    // ✅ 2) 단일 보고서 상세
    public ExpenseReportResponse getReport (Long id){
        ExpenseReport r = expenseReportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + id));

        var warnings = PolicyEngine.evaluateReportWarnings(r);
        var flags = warnings.stream().map(PolicyEngine.Warning::getMessage).toList();

        return ExpenseReportResponse.builder()
                .id(r.getId())
                .title(r.getTitle())
                .totalAmount(r.getTotalAmount())
                .status(r.getStatus().name())
                .destination(r.getDestination())
                .departureDate(r.getDepartureDate())
                .returnDate(r.getReturnDate())
                .createdAt(r.getCreatedAt())
                .approvedAt(r.getApprovedAt())
                .submitterId(r.getSubmitter() != null ? r.getSubmitter().getId() : null)
                .submitterName(r.getSubmitter() != null ? r.getSubmitter().getName() : null)
                .approverId(r.getApprover() != null ? r.getApprover().getId() : null)
                .approverName(r.getApprover() != null ? r.getApprover().getName() : null)
                .approvalComment(r.getApprovalComment())
                .flagged(!flags.isEmpty())
                .policyFlags(flags)
                .policyWarnings(warnings.stream().map(w -> com.example.demo.dto.PolicyWarningResponse.builder()
                        .code(w.getCode())
                        .message(w.getMessage())
                        .build()).toList())
                .items(
                        r.getItems().stream()
                                .map(i -> ExpenseItemResponse.builder()
                                        .id(i.getId())
                                        .date(i.getDate())
                                        .description(i.getDescription())
                                        .amount(i.getAmount())
                                        .category(i.getCategory())
                                        .build()
                                )
                                .toList()
                )
                .build();
    }

    // ExpenseReportService.java 파일에 추가할 내용

    // ✅ 3) 특정 사용자의 특정 상태 보고서 목록
    public List<ExpenseReportListItemResponse> findBySubmitterAndStatus(Long submitterId, ExpenseReportStatus status) {
        List<ExpenseReport> reports =
                expenseReportRepository.findBySubmitterIdAndStatus(submitterId, status);

        return reports.stream()
                .map(r -> {
                    boolean flagged = !PolicyEngine.evaluateReport(r).isEmpty();
                    return ExpenseReportListItemResponse.builder()
                            .id(r.getId())
                            .title(r.getTitle())
                            .totalAmount(r.getTotalAmount())
                            .status(r.getStatus().name())
                            .destination(r.getDestination())
                            .departureDate(r.getDepartureDate())
                            .returnDate(r.getReturnDate())
                            .flagged(flagged)
                            .build();
                })
                .toList();
    }

    public List<ExpenseReportListItemResponse> getReportsPendingApproval() {
        List<ExpenseReport> reports =
                expenseReportRepository.findByStatus(ExpenseReportStatus.SUBMITTED);

        return reports.stream()
                .map(r -> {
                    boolean flagged = !PolicyEngine.evaluateReport(r).isEmpty();
                    return ExpenseReportListItemResponse.builder()
                            .id(r.getId())
                            .title(r.getTitle())
                            .totalAmount(r.getTotalAmount())
                            .status(r.getStatus().name())
                            .destination(r.getDestination())
                            .departureDate(r.getDepartureDate())
                            .returnDate(r.getReturnDate())
                            .flagged(flagged)
                            .build();
                })
                .toList();
    }

    /**
     * Demo-friendly search endpoint.
     *
     * If requesterRole is not MANAGER/FINANCE, results are restricted to requesterId (submitter).
     */
    public List<ExpenseReportListItemResponse> searchReports(Long requesterId, String requesterRole, String q, String status, Double minTotal, Double maxTotal, String sort) {
        boolean approver = requesterRole != null && (
                requesterRole.equalsIgnoreCase("MANAGER") || requesterRole.equalsIgnoreCase("FINANCE")
        );

        Long submitterId = approver ? null : requesterId;

        ExpenseReportStatus st = null;
        if (status != null && !status.isBlank()) {
            st = ExpenseReportStatus.valueOf(status.trim().toUpperCase());
        }

        var list = expenseReportRepository.search(submitterId, q, st, minTotal, maxTotal);

        // Sort in-memory for simplicity (demo scale). Options:
        // - activity_desc: approvedAt/createdAt desc
        // - total_desc / total_asc
        if (sort != null && !sort.isBlank()) {
            switch (sort) {
                case "activity_desc" -> list.sort((a, b) -> {
                    var aT = a.getApprovedAt() != null ? a.getApprovedAt() : a.getCreatedAt();
                    var bT = b.getApprovedAt() != null ? b.getApprovedAt() : b.getCreatedAt();
                    return bT.compareTo(aT);
                });
                case "total_desc" -> list.sort((a, b) -> Double.compare(b.getTotalAmount(), a.getTotalAmount()));
                case "total_asc" -> list.sort((a, b) -> Double.compare(a.getTotalAmount(), b.getTotalAmount()));
                default -> {
                }
            }
        }

        return list.stream()
                .map(r -> {
                    boolean flagged = !PolicyEngine.evaluateReport(r).isEmpty();
                    return ExpenseReportListItemResponse.builder()
                            .id(r.getId())
                            .title(r.getTitle())
                            .totalAmount(r.getTotalAmount())
                            .status(r.getStatus().name())
                            .destination(r.getDestination())
                            .departureDate(r.getDepartureDate())
                            .returnDate(r.getReturnDate())
                            .flagged(flagged)
                            .build();
                })
                .toList();
    }

    public List<com.example.demo.dto.ExpenseReportActivityItem> getRecentActivity(Long requesterId, String requesterRole, int limit) {
        boolean approver = requesterRole != null && (
                requesterRole.equalsIgnoreCase("MANAGER") || requesterRole.equalsIgnoreCase("FINANCE")
        );
        Long submitterId = approver ? null : requesterId;

        return expenseReportRepository.recentActivity(submitterId)
                .stream()
                .limit(Math.max(1, Math.min(limit, 20)))
                .map(r -> {
                    var last = r.getApprovedAt() != null ? r.getApprovedAt() : r.getCreatedAt();
                    String label;
                    if (r.getApprovedAt() != null) {
                        label = r.getStatus() == ExpenseReportStatus.APPROVED ? "Approved" : "Rejected";
                    } else {
                        label = "Created";
                    }
                    boolean flagged = !PolicyEngine.evaluateReport(r).isEmpty();
                    return com.example.demo.dto.ExpenseReportActivityItem.builder()
                            .id(r.getId())
                            .title(r.getTitle())
                            .status(r.getStatus().name())
                            .totalAmount(r.getTotalAmount())
                            .submitterId(r.getSubmitter() != null ? r.getSubmitter().getId() : null)
                            .submitterName(r.getSubmitter() != null ? r.getSubmitter().getName() : null)
                            .createdAt(r.getCreatedAt())
                            .approvedAt(r.getApprovedAt())
                            .lastActivityAt(last)
                            .activityLabel(label)
                            .flagged(flagged)
                            .build();
                })
                .toList();
    }

    /**
     * Submit report.
     * If policy warnings exist, creates/updates a SpecialReview and routes to FINANCE_SPECIAL_REVIEW.
     */
    public ExpenseReportStatus submitReport(Long reportId, com.example.demo.dto.SubmitRequest req) {
        ExpenseReport report = expenseReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        if (req == null || req.getSubmitterId() == null) {
            throw new IllegalArgumentException("submitterId is required");
        }

        if (report.getSubmitter() == null || !report.getSubmitter().getId().equals(req.getSubmitterId())) {
            throw new IllegalStateException("Only the submitter can submit this report.");
        }

        if (report.getStatus() != ExpenseReportStatus.DRAFT && report.getStatus() != ExpenseReportStatus.CHANGES_REQUESTED) {
            throw new IllegalStateException("Only DRAFT/CHANGES_REQUESTED reports can be submitted.");
        }

        var warnings = PolicyEngine.evaluateReportWarnings(report);
        if (warnings.isEmpty()) {
            // Clear any previous special review
            specialReviewRepository.findByReportId(reportId).ifPresent(specialReviewRepository::delete);
            report.setStatus(ExpenseReportStatus.SUBMITTED);
            expenseReportRepository.save(report);
            return report.getStatus();
        }

        // Build reason map
        var reasonMap = new java.util.HashMap<String, String>();
        if (req.getReasons() != null) {
            for (var r : req.getReasons()) {
                if (r == null || r.code == null) continue;
                reasonMap.put(r.code, r.reason);
            }
        }

        // Require reason for each warning
        for (var w : warnings) {
            var reason = reasonMap.get(w.getCode());
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Reason is required for policy warning: " + w.getCode());
            }
        }

        SpecialReview review = specialReviewRepository.findByReportId(reportId)
                .orElseGet(() -> SpecialReview.builder()
                        .report(report)
                        .createdAt(java.time.LocalDateTime.now())
                        .status(SpecialReviewStatus.PENDING)
                        .build());

        review.setStatus(SpecialReviewStatus.PENDING);
        review.setDecidedAt(null);
        review.setReviewer(null);
        review.setReviewerComment(null);

        // Replace items
        review.getItems().clear();
        for (var w : warnings) {
            String reason = reasonMap.get(w.getCode());
            SpecialReviewItem item = SpecialReviewItem.builder()
                    .review(review)
                    .code(w.getCode())
                    .message(w.getMessage())
                    .employeeReason(reason)
                    .financeDecision(null)
                    .financeReason(null)
                    .build();
            review.getItems().add(item);
        }

        specialReviewRepository.save(review);

        report.setStatus(ExpenseReportStatus.FINANCE_SPECIAL_REVIEW);
        expenseReportRepository.save(report);
        return report.getStatus();
    }

    public com.example.demo.dto.SpecialReviewResponse getSpecialReview(Long reportId) {
        var review = specialReviewRepository.findByReportId(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Special review not found for report: " + reportId));

        return com.example.demo.dto.SpecialReviewResponse.builder()
                .id(review.getId())
                .status(review.getStatus().name())
                .createdAt(review.getCreatedAt())
                .decidedAt(review.getDecidedAt())
                .reviewerId(review.getReviewer() != null ? review.getReviewer().getId() : null)
                .reviewerName(review.getReviewer() != null ? review.getReviewer().getName() : null)
                .reviewerComment(review.getReviewerComment())
                .items(review.getItems().stream().map(it -> com.example.demo.dto.SpecialReviewItemResponse.builder()
                        .id(it.getId())
                        .code(it.getCode())
                        .message(it.getMessage())
                        .employeeReason(it.getEmployeeReason())
                        .financeDecision(it.getFinanceDecision() != null ? it.getFinanceDecision().name() : null)
                        .financeReason(it.getFinanceReason())
                        .build()).toList())
                .build();
    }

    public ExpenseReportStatus decideSpecialReview(Long reportId, com.example.demo.dto.SpecialReviewDecisionRequest req) {
        ExpenseReport report = expenseReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        if (report.getStatus() != ExpenseReportStatus.FINANCE_SPECIAL_REVIEW) {
            throw new IllegalStateException("Report is not in FINANCE_SPECIAL_REVIEW.");
        }

        if (req == null || req.getReviewerId() == null) {
            throw new IllegalArgumentException("reviewerId is required");
        }

        boolean isFinance = req.getReviewerRole() != null && req.getReviewerRole().equalsIgnoreCase("FINANCE");
        if (!isFinance) {
            throw new IllegalStateException("Only FINANCE can approve special reviews.");
        }

        User reviewer = userRepository.findById(req.getReviewerId())
                .orElseThrow(() -> new IllegalArgumentException("Reviewer not found: " + req.getReviewerId()));

        SpecialReview review = specialReviewRepository.findByReportId(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Special review not found for report: " + reportId));

        // Apply decisions by code
        var decisionMap = new java.util.HashMap<String, com.example.demo.dto.SpecialReviewDecisionRequest.ItemDecision>();
        if (req.getDecisions() != null) {
            for (var d : req.getDecisions()) {
                if (d == null || d.code == null) continue;
                decisionMap.put(d.code, d);
            }
        }

        boolean anyReject = false;
        for (var item : review.getItems()) {
            var d = decisionMap.get(item.getCode());
            if (d == null || d.decision == null) {
                throw new IllegalArgumentException("Decision required for warning: " + item.getCode());
            }
            var dec = SpecialReviewDecision.valueOf(d.decision.trim().toUpperCase());
            item.setFinanceDecision(dec);
            item.setFinanceReason(d.financeReason);
            if (dec == SpecialReviewDecision.REJECT) {
                anyReject = true;
            }
        }

        review.setReviewer(reviewer);
        review.setReviewerComment(req.getReviewerComment());
        review.setDecidedAt(java.time.LocalDateTime.now());
        review.setStatus(anyReject ? SpecialReviewStatus.REJECTED : SpecialReviewStatus.APPROVED);
        specialReviewRepository.save(review);

        if (anyReject) {
            if (req.getReviewerComment() == null || req.getReviewerComment().isBlank()) {
                throw new IllegalArgumentException("Reviewer reject reason is required.");
            }
            report.setStatus(ExpenseReportStatus.CHANGES_REQUESTED);
            expenseReportRepository.save(report);
            return report.getStatus();
        }

        // Approved special review: clear special review records and route to normal submission.
        specialReviewRepository.delete(review);
        report.setStatus(ExpenseReportStatus.SUBMITTED);
        expenseReportRepository.save(report);
        return report.getStatus();
    }

    // Approved
    public void approveReport(Long reportId, ApprovalRequest req) {
        ExpenseReport report = expenseReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        User approver = userRepository.findById(req.getApproverId())
                .orElseThrow(() -> new IllegalArgumentException("Approver not found: " + req.getApproverId()));

        if (report.getSubmitter().equals(approver)) {
            throw new IllegalStateException("You cannot approve/reject your own report.");
        }

        // 간단한 상태 체크 (원하면 더 엄격하게)
        if (report.getStatus() != ExpenseReportStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED status reports can be approved.");
        }

        report.setStatus(ExpenseReportStatus.APPROVED);
        report.setApprover(approver);
        report.setApprovedAt(LocalDateTime.now());
        report.setApprovalComment(req.getComment());

        expenseReportRepository.save(report);
    }

    // Reject
    public void rejectReport(Long reportId, ApprovalRequest req) {
        ExpenseReport report = expenseReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        User approver = userRepository.findById(req.getApproverId())
                .orElseThrow(() -> new IllegalArgumentException("Approver not found: " + req.getApproverId()));

        if (report.getSubmitter().equals(approver)) {
            throw new IllegalStateException("You cannot approve/reject your own report.");
        }

        if (report.getStatus() != ExpenseReportStatus.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED status reports can be rejected.");
        }

        report.setStatus(ExpenseReportStatus.REJECTED);
        report.setApprover(approver);
        report.setApprovedAt(LocalDateTime.now()); // 반려도 처리일자 기록
        report.setApprovalComment(req.getComment());

        expenseReportRepository.save(report);
    }

}