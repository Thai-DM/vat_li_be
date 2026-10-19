package com.vatly1.example.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Ensures that the check constraint 'exam_attempts_status_check' on PostgreSQL
 * includes all valid statuses ('IN_PROGRESS', 'SUBMITTED', 'GRADED', 'DISQUALIFIED').
 * This prevents DataIntegrityViolationException when an instructor disqualifies an attempt.
 */
@Component
@Order(10)
@Slf4j
@RequiredArgsConstructor
public class ExamConstraintInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection()) {
            String dbProduct = conn.getMetaData().getDatabaseProductName();
            if (dbProduct != null && dbProduct.toUpperCase().contains("POSTGRES")) {
                try {
                    jdbcTemplate.execute("ALTER TABLE exam_attempts DROP CONSTRAINT IF EXISTS exam_attempts_status_check;");
                    jdbcTemplate.execute("ALTER TABLE exam_attempts ADD CONSTRAINT exam_attempts_status_check CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'GRADED', 'DISQUALIFIED'));");
                    log.info("ExamConstraintInitializer: Successfully verified/updated 'exam_attempts_status_check' constraint to include DISQUALIFIED.");
                } catch (Exception e) {
                    log.warn("ExamConstraintInitializer: Notice while updating 'exam_attempts_status_check': {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("ExamConstraintInitializer: Could not inspect database metadata: {}", e.getMessage());
        }
    }
}
