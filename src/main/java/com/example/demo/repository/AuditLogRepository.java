package com.example.demo.repository;

import com.example.demo.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByReportIdOrderByCreatedAtAsc(Long reportId);
}
