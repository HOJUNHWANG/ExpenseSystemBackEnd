package com.example.demo.service;

import com.example.demo.domain.*;
import com.example.demo.repository.ExpenseReportRepository;
import com.example.demo.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DemoDataService {

    private final ExpenseReportRepository expenseReportRepository;
    private final com.example.demo.repository.ExpenseItemRepository expenseItemRepository;
    private final com.example.demo.repository.SpecialReviewItemRepository specialReviewItemRepository;
    private final com.example.demo.repository.SpecialReviewRepository specialReviewRepository;
    private final UserRepository userRepository;

    @Transactional
    public void resetAndSeed() {
        // IMPORTANT (Postgres): bulk deletes do NOT trigger JPA cascades.
        // Delete child tables first to avoid FK constraint violations.
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

        // 1) DRAFT (no warnings): submit → approval chain
        seedReport(employee, null,
                "Draft — Local Lunch",
                "New York",
                LocalDate.now().minusDays(2),
                LocalDate.now().minusDays(2),
                ExpenseReportStatus.DRAFT,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(2)).description("Lunch").amount(18.50).category("Meals").build()
                ));

        // Extra DRAFT: travel
        seedReport(employee, null,
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

        // 2) DRAFT (has warnings): submit requires per-warning reasons → CFO_SPECIAL_REVIEW
        // Hotel > $300/night triggers warning; also add >=$25 so receipt warning could apply depending on PolicyEngine.
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

        seedSpecialReviewPending(needsFinance, employee,
                List.of(
                        new SeedWarning("HOTEL_ABOVE_CAP", "Hotel above nightly cap ($250)", "Client conference rate was higher."),
                        new SeedWarning("AIRFARE_ABOVE_CAP", "Airfare above cap ($1000)", "Last-minute flight price spike.")
                ));

        // 3) CHANGES_REQUESTED: finance rejected at least one exception item (includes per-item financeReason)
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

        seedSpecialReviewRejected(changesRequested, employee, cfo,
                "Please revise meals to align with policy.",
                List.of(
                        new SeedDecision("MEALS_ABOVE_DAILY_CAP", "Meals exceed daily cap ($75)", "Team dinner during onsite work.", SpecialReviewDecision.REJECT, "Not eligible under meals policy; please split personal portion.")
                ));

        // 4) MANAGER_REVIEW (pending manager approval): keep approval queue populated
        seedReport(employee, null,
                "Submitted — NYC Trip",
                "New York, United States",
                LocalDate.now().minusDays(4),
                LocalDate.now().minusDays(2),
                ExpenseReportStatus.MANAGER_REVIEW,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(4)).description("Airfare").amount(320.45).category("Airfare").build(),
                        // Keep under $250 nightly cap so this seeded MANAGER_REVIEW report is clean.
                        ExpenseItem.builder().date(LocalDate.now().minusDays(3)).description("Hotel").amount(240.00).category("Hotel").build(),
                        // Keep under meal daily cap ($75)
                        ExpenseItem.builder().date(LocalDate.now().minusDays(3)).description("Meal").amount(58.90).category("Meal").build()
                ));

        seedReport(employee, null,
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

        // CFO review queue example (created by Manager)
        seedReport(manager, null,
                "Manager submitted — Vendor dinner",
                "New York, United States",
                LocalDate.now().minusDays(5),
                LocalDate.now().minusDays(5),
                ExpenseReportStatus.CFO_REVIEW,
                null,
                List.of(
                        // Keep under entertainment cap ($100) so CFO_REVIEW example is clean.
                        ExpenseItem.builder().date(LocalDate.now().minusDays(5)).description("Team dinner with vendor").amount(90.00).category("Entertainment").build()
                ));

        // CEO review queue example (created by CFO)
        seedReport(cfo, null,
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

        // CFO review queue example (created by CEO)
        seedReport(ceo, null,
                "CEO submitted — Executive offsite",
                "Boston, United States",
                LocalDate.now().minusDays(9),
                LocalDate.now().minusDays(9),
                ExpenseReportStatus.CFO_REVIEW,
                null,
                List.of(
                        // Keep under meal daily cap ($75)
                        ExpenseItem.builder().date(LocalDate.now().minusDays(9)).description("Executive lunch").amount(68.00).category("Meal").build()
                ));

        // 5) APPROVED
        seedReport(employee, manager,
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

        // 6) REJECTED (manager)
        seedReport(employee, manager,
                "Rejected — Missing details",
                "New Jersey",
                LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(30),
                ExpenseReportStatus.REJECTED,
                "Please add item details and resubmit.",
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(30)).description("Ride share").amount(23.40).category("Transport").build()
                ));
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

