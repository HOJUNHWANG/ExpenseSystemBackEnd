package com.example.demo.service;

import com.example.demo.domain.ExpenseItem;
import com.example.demo.domain.ExpenseReport;
import com.example.demo.domain.ExpenseReportStatus;
import com.example.demo.domain.User;
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

        report.setStatus(ExpenseReportStatus.SUBMITTED);

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

        var flags = PolicyEngine.evaluateReport(r);

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