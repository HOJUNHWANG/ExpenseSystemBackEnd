package com.example.demo.service;

import com.example.demo.domain.ExpenseItem;
import com.example.demo.domain.ExpenseReport;
import com.example.demo.domain.ExpenseReportStatus;
import com.example.demo.domain.User;
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
    private final UserRepository userRepository;

    @Transactional
    public void resetAndSeed() {
        // delete reports first (items cascade + orphanRemoval)
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

        User finance = userRepository.save(User.builder()
                .name("Finance Lee")
                .email("finance@example.com")
                .role("FINANCE")
                .build());

        // seeded reports
        seedReport(employee, null,
                "NYC Trip — Feb 2026",
                "New York",
                LocalDate.now().minusDays(4),
                LocalDate.now().minusDays(2),
                ExpenseReportStatus.SUBMITTED,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(4)).description("Flight").amount(320.45).category("Travel").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(3)).description("Hotel").amount(410.00).category("Lodging").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(3)).description("Meals").amount(58.90).category("Meals").build()
                ));

        seedReport(employee, manager,
                "Client Visit — Boston",
                "Boston",
                LocalDate.now().minusDays(10),
                LocalDate.now().minusDays(9),
                ExpenseReportStatus.APPROVED,
                "Looks good.",
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(10)).description("Train").amount(89.00).category("Travel").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(9)).description("Meals").amount(34.75).category("Meals").build()
                ));

        seedReport(employee, finance,
                "Conference — Chicago",
                "Chicago",
                LocalDate.now().minusDays(20),
                LocalDate.now().minusDays(17),
                ExpenseReportStatus.REJECTED,
                "Receipt missing for hotel charge.",
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(20)).description("Flight").amount(280.10).category("Travel").build(),
                        ExpenseItem.builder().date(LocalDate.now().minusDays(19)).description("Hotel").amount(590.00).category("Lodging").build()
                ));

        // keep a second SUBMITTED so approval queue isn't empty
        seedReport(employee, null,
                "Local Travel — NJ",
                "New Jersey",
                LocalDate.now().minusDays(1),
                LocalDate.now().minusDays(1),
                ExpenseReportStatus.SUBMITTED,
                null,
                List.of(
                        ExpenseItem.builder().date(LocalDate.now().minusDays(1)).description("Ride share").amount(23.40).category("Transport").build()
                ));
    }

    private void seedReport(
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

        expenseReportRepository.save(report);
    }
}
