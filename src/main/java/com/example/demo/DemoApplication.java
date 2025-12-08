package com.example.demo;

import com.example.demo.domain.ExpenseItem;
import com.example.demo.domain.ExpenseReport;
import com.example.demo.domain.User;
import com.example.demo.repository.ExpenseReportRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@Bean
	public CommandLineRunner initData(UserRepository userRepository,
									  ExpenseReportRepository expenseReportRepository) {
		return args -> {

			// 1) User 생성 (기존과 동일)
			User user = userRepository.findAll().stream().findFirst()
					.orElseGet(() -> userRepository.save(
							User.builder()
									.name("Jun")
									.email("jun@example.com")
									.role("EMPLOYEE")
									.build()
					));

			System.out.println("👉 User id = " + user.getId());

			// 2) ExpenseReport + ExpenseItem 더미 데이터 생성 (한 번만)
			if (expenseReportRepository.count() == 0) {
				ExpenseItem taxi = ExpenseItem.builder()
						.date(LocalDate.now())
						.description("Uber to Airport")
						.amount(35.67)
						.category("Transportation")
						.build();

				ExpenseItem meal = ExpenseItem.builder()
						.date(LocalDate.now())
						.description("Lunch")
						.amount(22.74)
						.category("Meal")
						.build();

				ExpenseReport report = ExpenseReport.builder()
						.title("December Expense Report")
						.createdAt(LocalDateTime.now())
						.status("SUBMITTED")
						.submitter(user)
						.build();

				// 양방향 연결
				taxi.setExpenseReport(report);
				meal.setExpenseReport(report);

				report.getItems().addAll(List.of(taxi, meal));
				report.setTotalAmount(taxi.getAmount() + meal.getAmount());

				ExpenseReport saved = expenseReportRepository.save(report);
				System.out.println("👉 Sample ExpenseReport saved: id=" + saved.getId());
			}
		};
	}
}
