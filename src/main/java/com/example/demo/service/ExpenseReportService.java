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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

        report.setStatus(String.valueOf(ExpenseReportStatus.SUBMITTED));

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
                .map(r -> ExpenseReportListItemResponse.builder()
                        .id(r.getId())
                        .title(r.getTitle())
                        .totalAmount(r.getTotalAmount())
                        .status(r.getStatus())
                        .destination(r.getDestination())
                        .departureDate(r.getDepartureDate())
                        .returnDate(r.getReturnDate())
                        .build()
                )
                .toList();
    }

    // ✅ 2) 단일 보고서 상세
    public ExpenseReportResponse getReport (Long id){
        ExpenseReport r = expenseReportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + id));

        return ExpenseReportResponse.builder()
                .id(r.getId())
                .title(r.getTitle())
                .totalAmount(r.getTotalAmount())
                .status(r.getStatus())
                .destination(r.getDestination())
                .departureDate(r.getDepartureDate())
                .returnDate(r.getReturnDate())
                .createdAt(r.getCreatedAt())
                .approvedAt(r.getApprovedAt())
                .submitterId(r.getSubmitter() != null ? r.getSubmitter().getId() : null)
                .submitterName(r.getSubmitter() != null ? r.getSubmitter().getName() : null)
                .approverId(r.getApprover() != null ? r.getApprover().getId() : null)
                .approverName(r.getApprover() != null ? r.getApprover().getName() : null)
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

    // ✅ 승인
    public void approveReport(Long reportId, ApprovalRequest req) {
        ExpenseReport report = expenseReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        User approver = userRepository.findById(req.getApproverId())
                .orElseThrow(() -> new IllegalArgumentException("Approver not found: " + req.getApproverId()));

        // 간단한 상태 체크 (원하면 더 엄격하게)
        if (!"SUBMITTED".equals(report.getStatus())) {
            throw new IllegalStateException("Only SUBMITTED reports can be approved.");
        }

        report.setStatus(String.valueOf(ExpenseReportStatus.APPROVED));
        report.setApprover(approver);
        report.setApprovedAt(LocalDateTime.now());
        report.setApprovalComment(req.getComment());

        expenseReportRepository.save(report);
    }

    // ✅ 반려
    public void rejectReport(Long reportId, ApprovalRequest req) {
        ExpenseReport report = expenseReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        User approver = userRepository.findById(req.getApproverId())
                .orElseThrow(() -> new IllegalArgumentException("Approver not found: " + req.getApproverId()));

        if (!"SUBMITTED".equals(report.getStatus())) {
            throw new IllegalStateException("Only SUBMITTED reports can be rejected.");
        }

        report.setStatus(String.valueOf(ExpenseReportStatus.REJECTED));
        report.setApprover(approver);
        report.setApprovedAt(LocalDateTime.now()); // 반려도 처리일자 기록
        report.setApprovalComment(req.getComment());

        expenseReportRepository.save(report);
    }

}