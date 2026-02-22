package com.example.demo.service;

import com.example.demo.domain.*;
import com.example.demo.repository.AuditLogRepository;
import com.example.demo.repository.ExpenseReportRepository;
import com.example.demo.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DemoDataService {

    private final ExpenseReportRepository expenseReportRepository;
    private final com.example.demo.repository.ExpenseItemRepository expenseItemRepository;
    private final com.example.demo.repository.SpecialReviewItemRepository specialReviewItemRepository;
    private final com.example.demo.repository.SpecialReviewRepository specialReviewRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void resetAndSeed() {
        // IMPORTANT (Postgres): bulk deletes do NOT trigger JPA cascades.
        // Delete child tables first to avoid FK constraint violations.
        auditLogRepository.deleteAllInBatch();
        expenseItemRepository.deleteAllInBatch();
        specialReviewItemRepository.deleteAllInBatch();
        specialReviewRepository.deleteAllInBatch();
        expenseReportRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // users
        User employee = userRepository.save(User.builder()
                .name("Jun Employee")
                .email("jun@example.com")
                .role("EMPLOYEE")
                .build());

        User manager = userRepository.save(User.builder()
                .name("Manager Kim")
                .email("manager@example.com")
                .role("MANAGER")
                .build());

        User cfo = userRepository.save(User.builder()
                .name("CFO Lee")
                .email("finance@example.com")
                .role("CFO")
                .build());

        User ceo = userRepository.save(User.builder()
                .name("CEO Park")
                .email("ceo@example.com")
                .role("CEO")
                .build());

        // seeded reports (cover every major workflow state)
        LocalDateTime baseTime = LocalDateTime.now().minusDays(1);

        // 1) DRAFT (no warnings): submit → approval chain
        ExpenseReport r1 = seedReport(employee, null,
                "Draft — Local Lunch",
                "New York",
                LocalDate.now().minusDays(2),
                LocalDate.now().minusDays(2),
                ExpenseReportStatus.DRAFT,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(2)).description("Lunch").amount(18.50).category("Meals").build()
                ));
        seedAuditLog(r1, "CREATED", null, "DRAFT", employee, null, baseTime.minusHours(12));

        // Extra DRAFT: travel
        ExpenseReport r2 = seedReport(employee, null,
                "Draft — Office Supplies",
                "New York",
                LocalDate.now().minusDays(1),
                LocalDate.now().minusDays(1),
                ExpenseReportStatus.DRAFT,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(1)).description("Monitor cable").amount(19.99).category("Office").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(1)).description("Notebooks").amount(12.40).category("Office").build()
                ));
        seedAuditLog(r2, "CREATED", null, "DRAFT", employee, null, baseTime.minusHours(10));

        // 2) DRAFT (has warnings): submit requires per-warning reasons → CFO_SPECIAL_REVIEW
        ExpenseReport needsFinance = seedReport(employee, null,
                "Draft — Hotel Exception (needs Finance)",
                "Boston, United States",
                LocalDate.now().minusDays(6),
                LocalDate.now().minusDays(5),
                ExpenseReportStatus.CFO_SPECIAL_REVIEW,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(6)).description("Hotel").amount(410.00).category("Hotel").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(5)).description("Meal").amount(48.20).category("Meal").build()
                ));
        seedAuditLog(needsFinance, "CREATED", null, "DRAFT", employee, null, baseTime.minusDays(5));
        seedAuditLog(needsFinance, "SUBMITTED_FOR_REVIEW", "DRAFT", "CFO_SPECIAL_REVIEW", employee, null, baseTime.minusDays(4).plusHours(2));

        seedSpecialReviewPending(needsFinance, employee,
                List.of(
                        new SeedWarning("HOTEL_ABOVE_CAP", "Hotel above nightly cap ($250)", "Client conference rate was higher."),
                        new SeedWarning("AIRFARE_ABOVE_CAP", "Airfare above cap ($1000)", "Last-minute flight price spike.")
                ));

        // 3) CHANGES_REQUESTED: finance rejected at least one exception item
        ExpenseReport changesRequested = seedReport(employee, null,
                "Changes requested — Meals cap exception",
                "Chicago",
                LocalDate.now().minusDays(12),
                LocalDate.now().minusDays(10),
                ExpenseReportStatus.CHANGES_REQUESTED,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(12)).description("Lunch").amount(40.00).category("Meals").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(12)).description("Dinner").amount(55.00).category("Meals").build()
                ));
        seedAuditLog(changesRequested, "CREATED", null, "DRAFT", employee, null, baseTime.minusDays(10));
        seedAuditLog(changesRequested, "SUBMITTED_FOR_REVIEW", "DRAFT", "CFO_SPECIAL_REVIEW", employee, null, baseTime.minusDays(9));
        seedAuditLog(changesRequested, "EXCEPTION_REJECTED", "CFO_SPECIAL_REVIEW", "CHANGES_REQUESTED", cfo, "Please revise meals to align with policy.", baseTime.minusDays(8));

        seedSpecialReviewRejected(changesRequested, employee, cfo,
                "Please revise meals to align with policy.",
                List.of(
                        new SeedDecision("MEALS_ABOVE_DAILY_CAP", "Meals exceed daily cap ($75)", "Team dinner during onsite work.", SpecialReviewDecision.REJECT, "Not eligible under meals policy; please split personal portion.")
                ));

        // 4) MANAGER_REVIEW (pending manager approval): keep approval queue populated
        ExpenseReport r4a = seedReport(employee, null,
                "Submitted — NYC Trip",
                "New York, United States",
                LocalDate.now().minusDays(4),
                LocalDate.now().minusDays(2),
                ExpenseReportStatus.MANAGER_REVIEW,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(4)).description("Airfare").amount(320.45).category("Airfare").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(3)).description("Hotel").amount(240.00).category("Hotel").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(3)).description("Meal").amount(58.90).category("Meal").build()
                ));
        seedAuditLog(r4a, "CREATED", null, "DRAFT", employee, null, baseTime.minusDays(3));
        seedAuditLog(r4a, "SUBMITTED", "DRAFT", "MANAGER_REVIEW", employee, null, baseTime.minusDays(2));

        ExpenseReport r4b = seedReport(employee, null,
                "Submitted — Local Travel — NJ",
                "New Jersey, United States",
                LocalDate.now().minusDays(3),
                LocalDate.now().minusDays(3),
                ExpenseReportStatus.MANAGER_REVIEW,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(3)).description("Mileage").amount(42.00).category("Mileage").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(3)).description("Parking").amount(18.00).category("Transportation").build()
                ));
        seedAuditLog(r4b, "CREATED", null, "DRAFT", employee, null, baseTime.minusDays(2));
        seedAuditLog(r4b, "SUBMITTED", "DRAFT", "MANAGER_REVIEW", employee, null, baseTime.minusDays(1));

        // CFO review queue example (created by Manager)
        ExpenseReport r4c = seedReport(manager, null,
                "Manager submitted — Vendor dinner",
                "New York, United States",
                LocalDate.now().minusDays(5),
                LocalDate.now().minusDays(5),
                ExpenseReportStatus.CFO_REVIEW,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(5)).description("Team dinner with vendor").amount(90.00).category("Entertainment").build()
                ));
        seedAuditLog(r4c, "CREATED", null, "DRAFT", manager, null, baseTime.minusDays(4));
        seedAuditLog(r4c, "SUBMITTED", "DRAFT", "CFO_REVIEW", manager, null, baseTime.minusDays(3));

        // CEO review queue example (created by CFO)
        ExpenseReport r4d = seedReport(cfo, null,
                "CFO submitted — Board meeting travel",
                "Washington, DC, United States",
                LocalDate.now().minusDays(8),
                LocalDate.now().minusDays(7),
                ExpenseReportStatus.CEO_REVIEW,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(8)).description("Airfare").amount(480.00).category("Airfare").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(7)).description("Hotel").amount(245.00).category("Hotel").build()
                ));
        seedAuditLog(r4d, "CREATED", null, "DRAFT", cfo, null, baseTime.minusDays(7));
        seedAuditLog(r4d, "SUBMITTED", "DRAFT", "CEO_REVIEW", cfo, null, baseTime.minusDays(6));

        // CFO review queue example (created by CEO)
        ExpenseReport r4e = seedReport(ceo, null,
                "CEO submitted — Executive offsite",
                "Boston, United States",
                LocalDate.now().minusDays(9),
                LocalDate.now().minusDays(9),
                ExpenseReportStatus.CFO_REVIEW,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(9)).description("Executive lunch").amount(68.00).category("Meal").build()
                ));
        seedAuditLog(r4e, "CREATED", null, "DRAFT", ceo, null, baseTime.minusDays(8));
        seedAuditLog(r4e, "SUBMITTED", "DRAFT", "CFO_REVIEW", ceo, null, baseTime.minusDays(7));

        // 5) APPROVED
        ExpenseReport r5 = seedReport(employee, manager,
                "Approved — Client Visit",
                "Seattle",
                LocalDate.now().minusDays(20),
                LocalDate.now().minusDays(19),
                ExpenseReportStatus.APPROVED,
                "Approved. Thanks!",
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(20)).description("Train").amount(89.00).category("Travel").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(19)).description("Meals").amount(34.75).category("Meals").build()
                ));
        seedAuditLog(r5, "CREATED", null, "DRAFT", employee, null, baseTime.minusDays(18));
        seedAuditLog(r5, "SUBMITTED", "DRAFT", "MANAGER_REVIEW", employee, null, baseTime.minusDays(17));
        seedAuditLog(r5, "MANAGER_APPROVED", "MANAGER_REVIEW", "CFO_REVIEW", manager, null, baseTime.minusDays(16));
        seedAuditLog(r5, "CFO_APPROVED", "CFO_REVIEW", "APPROVED", cfo, "Approved. Thanks!", baseTime.minusDays(15));

        // 6) REJECTED (manager)
        ExpenseReport r6 = seedReport(employee, manager,
                "Rejected — Missing details",
                "New Jersey",
                LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(30),
                ExpenseReportStatus.REJECTED,
                "Please add item details and resubmit.",
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(30)).description("Ride share").amount(23.40).category("Transport").build()
                ));
        seedAuditLog(r6, "CREATED", null, "DRAFT", employee, null, baseTime.minusDays(28));
        seedAuditLog(r6, "SUBMITTED", "DRAFT", "MANAGER_REVIEW", employee, null, baseTime.minusDays(27));
        seedAuditLog(r6, "REJECTED", "MANAGER_REVIEW", "REJECTED", manager, "Please add item details and resubmit.", baseTime.minusDays(26));

        // 7) International DRAFT — per-diem $50/day x 3 = $150
        ExpenseReport r7 = seedReport(employee, null,
                "Draft — London Conference",
                "London, United Kingdom",
                LocalDate.now().minusDays(5),
                LocalDate.now().minusDays(2),
                ExpenseReportStatus.DRAFT,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(5)).description("Hotel").amount(280.00).category("Hotel").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(4)).description("Airfare").amount(650.00).category("Airfare").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(3)).description("Meal").amount(45.00).category("Meal").build()
                ));
        seedAuditLog(r7, "CREATED", null, "DRAFT", employee, null, baseTime.minusHours(8));

        // 8) Same-day domestic trip — per-diem $0
        ExpenseReport r8 = seedReport(employee, null,
                "Draft — Same-day DC Trip",
                "Washington, DC, United States",
                LocalDate.now().minusDays(1),
                LocalDate.now().minusDays(1),
                ExpenseReportStatus.DRAFT,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(1)).description("Train").amount(65.00).category("Transportation").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(1)).description("Lunch").amount(22.00).category("Meal").build()
                ));
        seedAuditLog(r8, "CREATED", null, "DRAFT", employee, null, baseTime.minusHours(6));

        // 9) Multi-day domestic APPROVED — per-diem $25/day x 6 = $150
        ExpenseReport r9 = seedReport(employee, manager,
                "Approved — LA Training",
                "Los Angeles, United States",
                LocalDate.now().minusDays(22),
                LocalDate.now().minusDays(16),
                ExpenseReportStatus.APPROVED,
                "Approved for training program.",
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(22)).description("Airfare").amount(350.00).category("Airfare").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(21)).description("Hotel (5 nights)").amount(1100.00).category("Hotel").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(20)).description("Taxi").amount(45.00).category("Transportation").build()
                ));
        seedAuditLog(r9, "CREATED", null, "DRAFT", employee, null, baseTime.minusDays(20));
        seedAuditLog(r9, "SUBMITTED", "DRAFT", "MANAGER_REVIEW", employee, null, baseTime.minusDays(19));
        seedAuditLog(r9, "MANAGER_APPROVED", "MANAGER_REVIEW", "CFO_REVIEW", manager, null, baseTime.minusDays(18));
        seedAuditLog(r9, "CFO_APPROVED", "CFO_REVIEW", "APPROVED", cfo, "Approved for training program.", baseTime.minusDays(17));

        // 10) International MANAGER_REVIEW — per-diem $50/day x 3 = $150
        ExpenseReport r10 = seedReport(employee, null,
                "Submitted — Tokyo Client Meeting",
                "Tokyo, Japan",
                LocalDate.now().minusDays(7),
                LocalDate.now().minusDays(4),
                ExpenseReportStatus.MANAGER_REVIEW,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(7)).description("Airfare").amount(1200.00).category("Airfare").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(6)).description("Hotel (3 nights)").amount(600.00).category("Hotel").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(5)).description("Client dinner").amount(85.00).category("Entertainment").build()
                ));
        seedAuditLog(r10, "CREATED", null, "DRAFT", employee, null, baseTime.minusDays(6));
        seedAuditLog(r10, "SUBMITTED", "DRAFT", "MANAGER_REVIEW", employee, null, baseTime.minusDays(5));
    }

    private ExpenseReport seedReport(
            User submitter,
            User approver,
            String title,
            String destination,
            LocalDate departure,
            LocalDate ret,
            ExpenseReportStatus status,
            String comment,
            List<ExpenseItem> items
    ) {
        ExpenseReport report = ExpenseReport.builder()
                .title(title)
                .createdAt(LocalDateTime.now().minusDays(1))
                .destination(destination)
                .departureDate(departure)
                .returnDate(ret)
                .submitter(submitter)
                .status(status)
                .build();

        double total = 0;
        for (ExpenseItem it : items) {
            it.setExpenseReport(report);
            report.getItems().add(it);
            total += it.getAmount();
        }

        // Compute per-diem
        if (departure != null && ret != null && departure.isBefore(ret)) {
            long days = ChronoUnit.DAYS.between(departure, ret);
            String lower = (destination != null) ? destination.toLowerCase() : "";
            boolean domestic = lower.contains("united states") || lower.endsWith(", us") || !lower.contains(",");
            double rate = domestic ? 25.0 : 50.0;
            report.setPerDiemDays((int) days);
            report.setPerDiemRate(rate);
            report.setPerDiemAmount(days * rate);
            total += report.getPerDiemAmount();
        }

        report.setTotalAmount(total);

        if (status == ExpenseReportStatus.APPROVED || status == ExpenseReportStatus.REJECTED) {
            report.setApprover(approver);
            report.setApprovedAt(LocalDateTime.now().minusHours(3));
            report.setApprovalComment(comment);
        }

        return expenseReportRepository.save(report);
    }

    private record SeedWarning(String code, String message, String employeeReason) {}

    private record SeedDecision(
            String code,
            String message,
            String employeeReason,
            SpecialReviewDecision financeDecision,
            String financeReason
    ) {}

    private void seedAuditLog(ExpenseReport report, String action, String fromStatus, String toStatus,
                              User actor, String comment, LocalDateTime createdAt) {
        auditLogRepository.save(AuditLog.builder()
                .report(report)
                .action(action)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actorId(actor.getId())
                .actorName(actor.getName())
                .comment(comment)
                .createdAt(createdAt)
                .build());
    }

    private void seedSpecialReviewPending(ExpenseReport report, User employee, List<SeedWarning> warnings) {
        SpecialReview review = SpecialReview.builder()
                .report(report)
                .status(SpecialReviewStatus.PENDING)
                .createdAt(LocalDateTime.now().minusHours(6))
                .decidedAt(null)
                .reviewer(null)
                .reviewerComment(null)
                .build();

        for (var w : warnings) {
            review.getItems().add(SpecialReviewItem.builder()
                    .review(review)
                    .code(w.code)
                    .message(w.message)
                    .employeeReason(w.employeeReason)
                    .financeDecision(null)
                    .financeReason(null)
                    .build());
        }

        specialReviewRepository.save(review);
    }

    private void seedSpecialReviewRejected(
            ExpenseReport report,
            User employee,
            User finance,
            String reviewerComment,
            List<SeedDecision> decisions
    ) {
        SpecialReview review = SpecialReview.builder()
                .report(report)
                .status(SpecialReviewStatus.REJECTED)
                .createdAt(LocalDateTime.now().minusDays(2))
                .decidedAt(LocalDateTime.now().minusDays(1))
                .reviewer(finance)
                .reviewerComment(reviewerComment)
                .build();

        for (var d : decisions) {
            review.getItems().add(SpecialReviewItem.builder()
                    .review(review)
                    .code(d.code)
                    .message(d.message)
                    .employeeReason(d.employeeReason)
                    .financeDecision(d.financeDecision)
                    .financeReason(d.financeReason)
                    .build());
        }

        specialReviewRepository.save(review);
    }
}

