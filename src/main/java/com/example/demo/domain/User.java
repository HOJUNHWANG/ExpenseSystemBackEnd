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

    // 나중에 department, status(재직/퇴사 여부) 등도 추가 가능
}
