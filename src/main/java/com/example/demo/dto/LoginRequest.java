package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    @Email(message = "Invalid email format")
    private String email;

    private String password;  // Optional for backward compat; demo password is "demo1234"

}
