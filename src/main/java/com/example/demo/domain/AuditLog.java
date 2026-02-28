package com.example.demo.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_report_id", columnList = "report_id"),
        @Index(name = "idx_audit_logs_created_at", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private ExpenseReport report;

    private String action;

    private String fromStatus;

    private String toStatus;

    private Long actorId;

    private String actorName;

    @Column(length = 2000)
    private String comment;

    private LocalDateTime createdAt;
}
