package com.example.demo.controller;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.domain.User;
import com.example.demo.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "Demo authentication (no real security)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

    @Operation(summary = "Login", description = "Looks up a demo user by email. No password required (demo only).")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null) {
            return ResponseEntity.status(404)
                    .body("User not found for email: " + request.getEmail());
        }

        LoginResponse response = LoginResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();

        return ResponseEntity.ok(response);
    }
}
