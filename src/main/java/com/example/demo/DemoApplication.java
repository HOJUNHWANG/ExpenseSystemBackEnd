package com.example.demo;

import com.example.demo.domain.User;
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
	public CommandLineRunner initData(UserRepository userRepository) {
		return args -> {
			// Minimal seed so demo login works even before a reset.
			if (userRepository.count() == 0) {

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

				User cfo = User.builder()
						.name("CFO Lee")
						.email("finance@example.com")
						.role("CFO")
						.build();

				User ceo = User.builder()
						.name("CEO Park")
						.email("ceo@example.com")
						.role("CEO")
						.build();

				userRepository.save(employee);
				userRepository.save(manager);
				userRepository.save(cfo);
				userRepository.save(ceo);

				System.out.println("👉 Seed users created.");
			}
		};
	}
}
