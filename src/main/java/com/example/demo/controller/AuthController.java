package com.example.demo.controller;

import com.example.demo.config.JwtUtil;
import com.example.demo.domain.User;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "JWT authentication — demo password: demo1234")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final JwtUtil jwtUtil;

    @Operation(summary = "Login", description = "Login with email + password. Returns JWT. Demo password: demo1234")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null) {
            return ResponseEntity.status(404)
                    .body("User not found for email: " + request.getEmail());
        }

        // Validate password if the user has one stored
        if (user.getPassword() != null && request.getPassword() != null) {
            if (!bCryptPasswordEncoder.matches(request.getPassword(), user.getPassword())) {
                return ResponseEntity.status(401).body("Invalid password");
            }
        }

        String token = jwtUtil.generateToken(user.getId(), user.getName(), user.getEmail(), user.getRole());

        LoginResponse response = LoginResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .token(token)
                .build();

        return ResponseEntity.ok(response);
    }
}
