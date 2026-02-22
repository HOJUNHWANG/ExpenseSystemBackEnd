package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI expenseSystemOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Expense System API")
                        .description("REST API for the Expense Report Management System. "
                                + "Supports multi-level approval workflows, policy enforcement, "
                                + "and exception review processes.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Expense System Team")
                                .email("support@example.com")));
    }
}
