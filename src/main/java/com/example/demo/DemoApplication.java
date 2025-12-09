package com.example.demo;

import com.example.demo.domain.User;
import com.example.demo.repository.ExpenseReportRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@Bean
	public CommandLineRunner initData(UserRepository userRepository,
									  ExpenseReportRepository expenseReportRepository) {
		return args -> {
			if (userRepository.count() == 0) {

				// 1) Test용 User 생성 (기존과 동일)
				User employee = User.builder()
						.name("Jun Employee")
						.email("jun@example.com")
						.role("EMPLOYEE")
						.build();

				User manager = User.builder()
						.name("Manager Kim")
						.email("manager@example.com")
						.role("MANAGER")
						.build();

				User finance = User.builder()
						.name("Finance Lee")
						.email("finance@example.com")
						.role("FINANCE")
						.build();

				userRepository.save(employee);
				userRepository.save(manager);
				userRepository.save(finance);

				System.out.println("👉 Seed users created.");

			}
		};
	}
}
