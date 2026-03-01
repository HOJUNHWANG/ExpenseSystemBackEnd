package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;          // PK, Auto Increment

    @Column(nullable = false)
    private String name;      // User Name

    @Column(unique = true, nullable = false)
    private String email;     // Company Email

    @Column(nullable = false)
    private String role;      // Role: EMPLOYEE, MANAGER, CFO, CEO etc...

    @Column(length = 60)
    private String password;  // BCrypt hash; nullable for backward compat
}
