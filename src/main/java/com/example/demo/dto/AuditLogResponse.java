package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogResponse {
    private Long id;
    private String action;
    private String fromStatus;
    private String toStatus;
    private Long actorId;
    private String actorName;
    private String comment;
    private LocalDateTime createdAt;
}
