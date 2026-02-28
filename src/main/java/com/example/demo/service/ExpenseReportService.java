package com.example.demo.service;

import com.example.demo.domain.*;
import com.example.demo.dto.*;
import com.example.demo.repository.AuditLogRepository;
import com.example.demo.repository.ExpenseReportRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseReportService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseReportService.class);

    private final ExpenseReportRepository expenseReportRepository;
    private final com.example.demo.repository.SpecialReviewRepository specialReviewRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    private void logAudit(ExpenseReport report, String action, String fromStatus, String toStatus, Long actorId, String actorName, String comment) {
        auditLogRepository.save(AuditLog.builder()
                .report(report)
                .action(action)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actorId(actorId)
                .actorName(actorName)
                .comment(comment)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public List<AuditLogResponse> getAuditLog(Long reportId) {
        return auditLogRepository.findByReportIdOrderByCreatedAtAsc(reportId).stream()
                .map(log -> AuditLogResponse.builder()
                        .id(log.getId())
                        .action(log.getAction())
                        .fromStatus(log.getFromStatus())
                        .toStatus(log.getToStatus())
                        .actorId(log.getActorId())
                        .actorName(log.getActorName())
                        .comment(log.getComment())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList();
    }

    private UserRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role is required");
        }
        try {
            return UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown role: " + role);
        }
    }

    private void validateNoDuplicateMealDates(List<ExpenseItem> items) {
        if (items == null || items.isEmpty()) return;
        var seen = new java.util.HashMap<java.time.LocalDate, Integer>();

        for (ExpenseItem it : items) {
            if (it == null) continue;
            if (it.getDate() == null) continue;

            boolean isMeal = false;
            if (it.getCategory() != null && it.getCategory().toLowerCase().contains("meal")) isMeal = true;
            if (it.getDescription() != null && it.getDescription().toLowerCase().contains("per diem")) isMeal = true;
            if (!isMeal) continue;

            int next = seen.getOrDefault(it.getDate(), 0) + 1;
            seen.put(it.getDate(), next);
            if (next > 1) {
                throw new IllegalArgumentException("Only one meal entry per date is allowed in this demo.");
            }
        }
    }

    private boolean isDomestic(String destination) {
        if (destination == null) return true;
        String lower = destination.toLowerCase();
        return lower.contains("united states") || lower.endsWith(", us")
                || !lower.contains(","); // no country separator → assume domestic
    }

    private void computePerDiem(ExpenseReport report) {
        if (report.getDepartureDate() == null || report.getReturnDate() == null) {
            report.setPerDiemDays(0);
            report.setPerDiemRate(0);
            report.setPerDiemAmount(0);
            return;
        }
        if (!report.getDepartureDate().isBefore(report.getReturnDate())) {
            // same-day trip → no per-diem
            report.setPerDiemDays(0);
            report.setPerDiemRate(0);
            report.setPerDiemAmount(0);
            return;
        }
        long days = ChronoUnit.DAYS.between(report.getDepartureDate(), report.getReturnDate());
        boolean domestic = isDomestic(report.getDestination());
        double rate = domestic ? 25.0 : 50.0;
        report.setPerDiemDays((int) days);
        report.setPerDiemRate(rate);
        report.setPerDiemAmount(days * rate);
    }

    @Transactional
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

        // 3) Each report must have at least one item (demo rule).
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("At least one item is required.");
        }

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

        // Compute per-diem and add to total
        computePerDiem(report);
        report.setTotalAmount(total + report.getPerDiemAmount());

        // Enforce meal rule (no duplicate meal entries by date)
        validateNoDuplicateMealDates(report.getItems());

        // 4) 저장 (cascade = ALL 덕분에 item들도 같이 저장됨)
        ExpenseReport saved = expenseReportRepository.save(report);

        logAudit(saved, "CREATED", null, "DRAFT", submitter.getId(), submitter.getName(), null);

        return saved.getId();
    }

    private ExpenseReportListItemResponse toListItem(ExpenseReport r) {
        boolean flagged = !PolicyEngine.evaluateReport(r).isEmpty();
        return ExpenseReportListItemResponse.builder()
                .id(r.getId())
                .title(r.getTitle())
                .totalAmount(r.getTotalAmount())
                .status(r.getStatus().name())
                .destination(r.getDestination())
                .departureDate(r.getDepartureDate())
                .returnDate(r.getReturnDate())
                .perDiemAmount(r.getPerDiemAmount())
                .perDiemRate(r.getPerDiemRate())
                .perDiemDays(r.getPerDiemDays())
                .flagged(flagged)
                .build();
    }

    private <T> PageResponse<T> toPageResponse(Page<?> page, List<T> content) {
        return PageResponse.<T>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    // --- Paginated endpoints ---

    public PageResponse<ExpenseReportListItemResponse> getReportsBySubmitterPaged(Long submitterId, int page, int size) {
        var result = expenseReportRepository.findBySubmitterId(submitterId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return toPageResponse(result, result.getContent().stream().map(this::toListItem).toList());
    }

    public PageResponse<ExpenseReportListItemResponse> findBySubmitterAndStatusPaged(Long submitterId, ExpenseReportStatus status, int page, int size) {
        var result = expenseReportRepository.findBySubmitterIdAndStatus(submitterId, status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return toPageResponse(result, result.getContent().stream().map(this::toListItem).toList());
    }

    public PageResponse<ExpenseReportListItemResponse> getReportsPendingApprovalPaged(String requesterRole, int page, int size) {
        UserRole role = parseRole(requesterRole);
        ExpenseReportStatus target = switch (role) {
            case MANAGER -> ExpenseReportStatus.MANAGER_REVIEW;
            case CFO -> ExpenseReportStatus.CFO_REVIEW;
            case CEO -> ExpenseReportStatus.CEO_REVIEW;
            default -> throw new IllegalArgumentException("Unknown requesterRole: " + requesterRole);
        };
        var result = expenseReportRepository.findByStatus(target, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return toPageResponse(result, result.getContent().stream().map(this::toListItem).toList());
    }

    public PageResponse<ExpenseReportListItemResponse> searchReportsPaged(Long requesterId, String requesterRole, String q, String status, Double minTotal, Double maxTotal, String sort, int page, int size) {
        UserRole role = parseRole(requesterRole);
        boolean approver = role == UserRole.MANAGER || role == UserRole.CFO || role == UserRole.CEO;
        Long submitterId = approver ? null : requesterId;

        ExpenseReportStatus st = null;
        if (status != null && !status.isBlank()) {
            st = ExpenseReportStatus.valueOf(status.trim().toUpperCase());
        }

        Sort jpaSort = switch (sort != null ? sort : "activity_desc") {
            case "total_desc" -> Sort.by(Sort.Direction.DESC, "totalAmount");
            case "total_asc" -> Sort.by(Sort.Direction.ASC, "totalAmount");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        var result = expenseReportRepository.searchPaged(submitterId, q, st, minTotal, maxTotal, PageRequest.of(page, size, jpaSort));
        return toPageResponse(result, result.getContent().stream().map(this::toListItem).toList());
    }

    // --- Stats ---

    public com.example.demo.dto.StatsResponse getStats() {
        var all = expenseReportRepository.findAllWithItems();

        long approved = all.stream().filter(r -> r.getStatus() == ExpenseReportStatus.APPROVED).count();
        long rejected = all.stream().filter(r -> r.getStatus() == ExpenseReportStatus.REJECTED).count();
        long pending = all.stream().filter(r -> {
            var s = r.getStatus();
            return s == ExpenseReportStatus.MANAGER_REVIEW || s == ExpenseReportStatus.CFO_REVIEW
                    || s == ExpenseReportStatus.CEO_REVIEW || s == ExpenseReportStatus.CFO_SPECIAL_REVIEW
                    || s == ExpenseReportStatus.CEO_SPECIAL_REVIEW;
        }).count();
        double totalAmount = all.stream().mapToDouble(ExpenseReport::getTotalAmount).sum();

        // By category (from line items)
        var categoryMap = new java.util.LinkedHashMap<String, double[]>(); // [amount, count]
        for (var r : all) {
            for (var item : r.getItems()) {
                String cat = item.getCategory() != null ? item.getCategory() : "Other";
                categoryMap.computeIfAbsent(cat, k -> new double[2]);
                categoryMap.get(cat)[0] += item.getAmount();
                categoryMap.get(cat)[1] += 1;
            }
        }
        var byCategory = categoryMap.entrySet().stream()
                .map(e -> com.example.demo.dto.StatsResponse.CategoryStat.builder()
                        .category(e.getKey())
                        .amount(e.getValue()[0])
                        .count((int) e.getValue()[1])
                        .build())
                .sorted((a, b) -> Double.compare(b.getAmount(), a.getAmount()))
                .toList();

        // By month (based on createdAt)
        var monthMap = new java.util.TreeMap<String, double[]>();
        for (var r : all) {
            if (r.getCreatedAt() == null) continue;
            String month = r.getCreatedAt().toLocalDate().withDayOfMonth(1).toString().substring(0, 7);
            monthMap.computeIfAbsent(month, k -> new double[2]);
            monthMap.get(month)[0] += r.getTotalAmount();
            monthMap.get(month)[1] += 1;
        }
        var byMonth = monthMap.entrySet().stream()
                .map(e -> com.example.demo.dto.StatsResponse.MonthStat.builder()
                        .month(e.getKey())
                        .amount(e.getValue()[0])
                        .count((int) e.getValue()[1])
                        .build())
                .toList();

        return com.example.demo.dto.StatsResponse.builder()
                .totalReports(all.size())
                .approved(approved)
                .rejected(rejected)
                .pending(pending)
                .totalAmount(totalAmount)
                .byCategory(byCategory)
                .byMonth(byMonth)
                .build();
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
                .perDiemAmount(r.getPerDiemAmount())
                .perDiemRate(r.getPerDiemRate())
                .perDiemDays(r.getPerDiemDays())
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

    public List<ExpenseReportListItemResponse> getReportsPendingApproval(String requesterRole) {
        UserRole role = parseRole(requesterRole);

        ExpenseReportStatus target = switch (role) {
            case MANAGER -> ExpenseReportStatus.MANAGER_REVIEW;
            case CFO -> ExpenseReportStatus.CFO_REVIEW;
            case CEO -> ExpenseReportStatus.CEO_REVIEW;
            default -> throw new IllegalArgumentException("Unknown requesterRole: " + requesterRole);
        };

        List<ExpenseReport> reports = expenseReportRepository.findByStatus(target);

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
     * If requesterRole is not MANAGER/CFO/CEO, results are restricted to requesterId (submitter).
     */
    public List<ExpenseReportListItemResponse> searchReports(Long requesterId, String requesterRole, String q, String status, Double minTotal, Double maxTotal, String sort) {
        UserRole role = parseRole(requesterRole);
        boolean approver = role == UserRole.MANAGER || role == UserRole.CFO || role == UserRole.CEO;

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
        UserRole role = parseRole(requesterRole);
        boolean approver = role == UserRole.MANAGER || role == UserRole.CFO || role == UserRole.CEO;
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
     * Update a report.
     *
     * Allowed only for the submitter when the report is in DRAFT or CHANGES_REQUESTED.
     */
    @Transactional
    public ExpenseReportStatus updateReport(Long reportId, com.example.demo.dto.ExpenseReportUpdateRequest req) {
        ExpenseReport report = expenseReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        if (req == null || req.getSubmitterId() == null) {
            throw new IllegalArgumentException("submitterId is required");
        }

        if (report.getSubmitter() == null || !report.getSubmitter().getId().equals(req.getSubmitterId())) {
            throw new IllegalStateException("Only the submitter can update this report.");
        }

        if (report.getStatus() != ExpenseReportStatus.DRAFT && report.getStatus() != ExpenseReportStatus.CHANGES_REQUESTED) {
            throw new IllegalStateException("Only DRAFT/CHANGES_REQUESTED reports can be updated.");
        }

        if (req.getTitle() != null) report.setTitle(req.getTitle());
        report.setDestination(req.getDestination());
        report.setDepartureDate(req.getDepartureDate());
        report.setReturnDate(req.getReturnDate());

        // Replace items
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("At least one item is required.");
        }

        report.getItems().clear();
        double total = 0;
        if (req.getItems() != null) {
            for (var itemReq : req.getItems()) {
                if (itemReq == null) continue;
                ExpenseItem item = ExpenseItem.builder()
                        .date(itemReq.getDate())
                        .description(itemReq.getDescription())
                        .amount(itemReq.getAmount())
                        .category(itemReq.getCategory())
                        .build();
                item.setExpenseReport(report);
                report.getItems().add(item);
                total += itemReq.getAmount();
            }
        }
        // Compute per-diem and add to total
        computePerDiem(report);
        report.setTotalAmount(total + report.getPerDiemAmount());

        // Enforce meal rule (no duplicate meal entries by date)
        validateNoDuplicateMealDates(report.getItems());

        expenseReportRepository.save(report);

        User submitter = report.getSubmitter();
        logAudit(report, "UPDATED", report.getStatus().name(), report.getStatus().name(),
                submitter.getId(), submitter.getName(), null);

        return report.getStatus();
    }

    @Transactional
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

        // Each report must have at least one item.
        if (report.getItems() == null || report.getItems().isEmpty()) {
            throw new IllegalArgumentException("At least one item is required.");
        }

        // Enforce meal rule (no duplicate meal entries by date)
        validateNoDuplicateMealDates(report.getItems());

        String previousStatus = report.getStatus().name();
        User submitter = report.getSubmitter();

        var warnings = PolicyEngine.evaluateReportWarnings(report);
        if (warnings.isEmpty()) {
            // Clear any previous exception-review record
            specialReviewRepository.findByReportId(reportId).ifPresent(specialReviewRepository::delete);

            // Route into normal approval chain based on submitter role.
            String rawRole = report.getSubmitter() != null ? report.getSubmitter().getRole() : null;
            UserRole submitterRole = parseRole(rawRole);

            switch (submitterRole) {
                case EMPLOYEE -> report.setStatus(ExpenseReportStatus.MANAGER_REVIEW);
                case MANAGER -> report.setStatus(ExpenseReportStatus.CFO_REVIEW);
                case CFO -> report.setStatus(ExpenseReportStatus.CEO_REVIEW);
                case CEO -> report.setStatus(ExpenseReportStatus.CFO_REVIEW);
            }

            expenseReportRepository.save(report);
            logAudit(report, "SUBMITTED", previousStatus, report.getStatus().name(),
                    submitter.getId(), submitter.getName(), null);
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

        // In this public demo we allow submitting without providing per-warning reasons.
        // (The UI can collect reasons, but we don't hard-require them to keep the flow frictionless.)

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
            String reason = reasonMap.getOrDefault(w.getCode(), "");
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

        // Exception review reviewer depends on who submitted:
        // - If CFO submits and still has exceptions, CEO reviews the exception
        // - Otherwise CFO reviews the exception
        String rawRole = report.getSubmitter() != null ? report.getSubmitter().getRole() : null;
        UserRole submitterRole = parseRole(rawRole);
        if (submitterRole == UserRole.CFO) {
            report.setStatus(ExpenseReportStatus.CEO_SPECIAL_REVIEW);
        } else {
            report.setStatus(ExpenseReportStatus.CFO_SPECIAL_REVIEW);
        }
        expenseReportRepository.save(report);
        logAudit(report, "SUBMITTED_FOR_REVIEW", previousStatus, report.getStatus().name(),
                submitter.getId(), submitter.getName(), null);
        return report.getStatus();
    }

    public com.example.demo.dto.SpecialReviewResponse getExceptionReview(Long reportId) {
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

    public com.example.demo.dto.SubmitterFeedbackResponse getSubmitterFeedback(Long reportId, Long requesterId) {
        ExpenseReport report = expenseReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        if (requesterId == null) {
            throw new IllegalArgumentException("requesterId is required");
        }

        if (report.getSubmitter() == null || !report.getSubmitter().getId().equals(requesterId)) {
            throw new IllegalStateException("Only the submitter can view feedback.");
        }

        var review = specialReviewRepository.findByReportId(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Feedback not found for report: " + reportId));

        return com.example.demo.dto.SubmitterFeedbackResponse.builder()
                .specialReviewStatus(review.getStatus().name())
                .decidedAt(review.getDecidedAt())
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

    @Transactional
    public ExpenseReportStatus decideExceptionReview(Long reportId, com.example.demo.dto.SpecialReviewDecisionRequest req) {
        ExpenseReport report = expenseReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        String previousStatus = report.getStatus().name();
        boolean cfoPath = report.getStatus() == ExpenseReportStatus.CFO_SPECIAL_REVIEW;
        boolean ceoPath = report.getStatus() == ExpenseReportStatus.CEO_SPECIAL_REVIEW;
        if (!cfoPath && !ceoPath) {
            throw new IllegalStateException("Report is not in a special review state.");
        }

        if (req == null || req.getReviewerId() == null) {
            throw new IllegalArgumentException("reviewerId is required");
        }

        UserRole reviewerRole = parseRole(req.getReviewerRole());
        if (cfoPath && reviewerRole != UserRole.CFO) {
            throw new IllegalStateException("Only CFO can approve CFO special reviews.");
        }
        if (ceoPath && reviewerRole != UserRole.CEO) {
            throw new IllegalStateException("Only CEO can approve CEO special reviews.");
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
                if (d.financeReason == null || d.financeReason.isBlank()) {
                    throw new IllegalArgumentException("Finance reason is required when rejecting: " + item.getCode());
                }
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
            logAudit(report, "EXCEPTION_REJECTED", previousStatus, report.getStatus().name(),
                    reviewer.getId(), reviewer.getName(), req.getReviewerComment());
            return report.getStatus();
        }

        // Approved exception review: clear review records and route to normal approval chain.
        specialReviewRepository.delete(review);

        String rawRole = report.getSubmitter() != null ? report.getSubmitter().getRole() : null;
        UserRole submitterRole = parseRole(rawRole);

        switch (submitterRole) {
            case EMPLOYEE -> report.setStatus(ExpenseReportStatus.MANAGER_REVIEW);
            case MANAGER -> report.setStatus(ExpenseReportStatus.CFO_REVIEW);
            case CFO -> report.setStatus(ExpenseReportStatus.CEO_REVIEW);
            case CEO -> report.setStatus(ExpenseReportStatus.CFO_REVIEW);
        }

        expenseReportRepository.save(report);
        logAudit(report, "EXCEPTION_APPROVED", previousStatus, report.getStatus().name(),
                reviewer.getId(), reviewer.getName(), req.getReviewerComment());
        return report.getStatus();
    }

    // Approved
    @Transactional
    public void approveReport(Long reportId, ApprovalRequest req) {
        ExpenseReport report = expenseReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        User approver = userRepository.findById(req.getApproverId())
                .orElseThrow(() -> new IllegalArgumentException("Approver not found: " + req.getApproverId()));

        if (report.getSubmitter().equals(approver)) {
            throw new IllegalStateException("You cannot approve/reject your own report.");
        }

        // Role-based status transition
        ExpenseReportStatus st = report.getStatus();

        UserRole approverRole = parseRole(approver.getRole());

        if (st == ExpenseReportStatus.MANAGER_REVIEW || st == ExpenseReportStatus.SUBMITTED /* legacy */) {
            if (approverRole != UserRole.MANAGER) {
                throw new IllegalStateException("Only MANAGER can approve MANAGER_REVIEW reports.");
            }
            report.setStatus(ExpenseReportStatus.CFO_REVIEW);
            expenseReportRepository.save(report);
            logAudit(report, "MANAGER_APPROVED", st.name(), report.getStatus().name(),
                    approver.getId(), approver.getName(), req.getComment());
            return;
        }

        if (st == ExpenseReportStatus.CFO_REVIEW) {
            if (approverRole != UserRole.CFO) {
                throw new IllegalStateException("Only CFO can approve CFO_REVIEW reports.");
            }
            report.setStatus(ExpenseReportStatus.APPROVED);
            report.setApprover(approver);
            report.setApprovedAt(LocalDateTime.now());
            report.setApprovalComment(req.getComment());
            expenseReportRepository.save(report);
            logAudit(report, "CFO_APPROVED", st.name(), report.getStatus().name(),
                    approver.getId(), approver.getName(), req.getComment());
            return;
        }

        if (st == ExpenseReportStatus.CEO_REVIEW) {
            if (approverRole != UserRole.CEO) {
                throw new IllegalStateException("Only CEO can approve CEO_REVIEW reports.");
            }
            report.setStatus(ExpenseReportStatus.APPROVED);
            report.setApprover(approver);
            report.setApprovedAt(LocalDateTime.now());
            report.setApprovalComment(req.getComment());
            expenseReportRepository.save(report);
            logAudit(report, "CEO_APPROVED", st.name(), report.getStatus().name(),
                    approver.getId(), approver.getName(), req.getComment());
            return;
        }

        throw new IllegalStateException("Only MANAGER_REVIEW/CFO_REVIEW/CEO_REVIEW reports can be approved.");
    }

    @Transactional
    public void deleteDraft(Long reportId, Long requesterId) {
        ExpenseReport report = expenseReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        if (requesterId == null) {
            throw new IllegalArgumentException("requesterId is required");
        }

        if (report.getSubmitter() == null || !report.getSubmitter().getId().equals(requesterId)) {
            throw new IllegalStateException("Only the submitter can delete this report.");
        }

        if (report.getStatus() != ExpenseReportStatus.DRAFT && report.getStatus() != ExpenseReportStatus.CHANGES_REQUESTED) {
            throw new IllegalStateException("Only DRAFT/CHANGES_REQUESTED reports can be deleted.");
        }

        // If an exception review exists for some reason, delete it too.
        specialReviewRepository.findByReportId(reportId).ifPresent(specialReviewRepository::delete);
        expenseReportRepository.delete(report);
    }

    // Reject
    @Transactional
    public void rejectReport(Long reportId, ApprovalRequest req) {
        ExpenseReport report = expenseReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        User approver = userRepository.findById(req.getApproverId())
                .orElseThrow(() -> new IllegalArgumentException("Approver not found: " + req.getApproverId()));

        if (report.getSubmitter().equals(approver)) {
            throw new IllegalStateException("You cannot approve/reject your own report.");
        }

        if (report.getStatus() != ExpenseReportStatus.MANAGER_REVIEW
                && report.getStatus() != ExpenseReportStatus.CFO_REVIEW
                && report.getStatus() != ExpenseReportStatus.CEO_REVIEW
                && report.getStatus() != ExpenseReportStatus.SUBMITTED /* legacy */) {
            throw new IllegalStateException("Only MANAGER_REVIEW/CFO_REVIEW/CEO_REVIEW reports can be rejected.");
        }

        String previousStatus = report.getStatus().name();
        report.setStatus(ExpenseReportStatus.REJECTED);
        report.setApprover(approver);
        report.setApprovedAt(LocalDateTime.now()); // 반려도 처리일자 기록
        report.setApprovalComment(req.getComment());

        expenseReportRepository.save(report);
        logAudit(report, "REJECTED", previousStatus, report.getStatus().name(),
                approver.getId(), approver.getName(), req.getComment());
    }

}
