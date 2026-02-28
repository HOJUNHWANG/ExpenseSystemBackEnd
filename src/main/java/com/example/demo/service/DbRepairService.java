package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Render Postgres hardening.
 *
 * Postgres can keep an enum CHECK constraint on the status column that becomes stale
 * as our workflow evolves. Hibernate ddl-auto=update does not reliably update CHECK constraints.
 *
 * If the constraint is stale, demo reset/seed can fail with:
 *  - violates check constraint "expense_reports_status_check"
 */
@Component
public class DbRepairService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DbRepairService.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public DbRepairService(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String dbName = dataSource.getConnection().getMetaData().getDatabaseProductName();
            if (dbName == null || !dbName.toLowerCase().contains("postgres")) {
                return;
            }
        } catch (Exception e) {
            log.warn("Could not detect database type, skipping constraint repair: {}", e.getMessage());
            return;
        }

        // Allowed statuses (keep legacy values for backward compatibility)
        String allowed = Arrays.stream(new String[]{
                "DRAFT",
                "SUBMITTED",
                "FINANCE_SPECIAL_REVIEW",
                "MANAGER_REVIEW",
                "CFO_REVIEW",
                "CEO_REVIEW",
                "CFO_SPECIAL_REVIEW",
                "CEO_SPECIAL_REVIEW",
                "CHANGES_REQUESTED",
                "APPROVED",
                "REJECTED"
        }).map(s -> "'" + s + "'")
                .collect(Collectors.joining(", "));

        // Drop stale constraint if present, then recreate.
        // Note: add constraint is idempotent with IF NOT EXISTS? (not supported) so we drop first.
        try {
            jdbcTemplate.execute("ALTER TABLE expense_reports DROP CONSTRAINT IF EXISTS expense_reports_status_check");
        } catch (Exception ignored) {
        }

        try {
            jdbcTemplate.execute("ALTER TABLE expense_reports ADD CONSTRAINT expense_reports_status_check CHECK (status IN (" + allowed + "))");
        } catch (Exception e) {
            // If this fails, we don't want to crash startup.
            log.error("Failed to recreate expense_reports_status_check constraint", e);
        }
    }
}
