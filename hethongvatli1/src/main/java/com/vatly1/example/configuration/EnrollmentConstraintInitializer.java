package com.vatly1.example.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Initializes and verifies Database constraints and partial unique indexes for enrollment integrity.
 * Strictly active ONLY under 'test' profile AND when running against H2 in-memory test database.
 * PostgreSQL, MySQL, and all production/staging databases MUST be initialized strictly via official migration scripts.
 */
@Component
@Profile("test")
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class EnrollmentConstraintInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        // Enforce guard: only execute in-memory emulation on H2 database
        try (Connection conn = dataSource.getConnection()) {
            String dbProduct = conn.getMetaData().getDatabaseProductName();
            if (dbProduct == null || !dbProduct.toUpperCase().contains("H2")) {
                log.info("Non-H2 database detected ('{}'). Skipping auto-DDL in EnrollmentConstraintInitializer. " +
                        "PostgreSQL test and production databases must strictly use official migration scripts.", dbProduct);
                return;
            }
        } catch (Exception e) {
            log.warn("Could not determine database product name. Skipping auto-DDL: {}", e.getMessage());
            return;
        }

        log.info("H2 database detected. Initializing in-memory constraint emulation for class enrollments integrity...");

        // 1. Composite Unique Constraint on classes (class_id, subject_id, semester_id)
        try {
            jdbcTemplate.execute(
                "ALTER TABLE classes ADD CONSTRAINT uq_classes_id_subject_semester UNIQUE (class_id, subject_id, semester_id)"
            );
            log.info("Constraint 'uq_classes_id_subject_semester' verified/created on classes.");
        } catch (Exception e) {
            log.debug("Constraint 'uq_classes_id_subject_semester' already exists or notice: {}", e.getMessage());
        }

        // 2. Composite Foreign Key from class_enrollments to classes
        try {
            jdbcTemplate.execute(
                "ALTER TABLE class_enrollments ADD CONSTRAINT fk_enrollment_class_subject_semester FOREIGN KEY (class_id, subject_id, semester_id) REFERENCES classes(class_id, subject_id, semester_id) ON DELETE CASCADE"
            );
            log.info("Constraint 'fk_enrollment_class_subject_semester' verified/created on class_enrollments.");
        } catch (Exception e) {
            log.debug("Constraint 'fk_enrollment_class_subject_semester' already exists or notice: {}", e.getMessage());
        }

        // 3. Emulate partial unique index in H2 via computed column + unique index
        try {
            jdbcTemplate.execute(
                "ALTER TABLE class_enrollments ADD COLUMN IF NOT EXISTS active_guard VARCHAR(255) GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN CAST(student_id AS VARCHAR) || '_' || CAST(subject_id AS VARCHAR) || '_' || CAST(semester_id AS VARCHAR) ELSE NULL END)"
            );
            jdbcTemplate.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS uq_active_enrollment_subject_semester ON class_enrollments (active_guard)"
            );
            log.info("Emulated partial unique index 'uq_active_enrollment_subject_semester' on H2 via generated column.");
        } catch (Exception ex3) {
            log.warn("Could not create partial unique index or H2 emulation: {}", ex3.getMessage());
        }
    }
}
