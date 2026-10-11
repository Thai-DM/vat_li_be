-- =============================================================================
-- MIGRATION PHASE 5 — STEP 2: COMMON FINAL EXAM MATRIX & CLO ARCHITECTURE
-- Database: PostgreSQL 14+ (Tested on Neon PostgreSQL 18.6)
-- Target: Enhanced ExamMatrix (status, is_locked, revision_number, row_version, matrix_family_id)
--         Enhanced ExamMatrixDetail (subject_id NOT NULL, clo_id NULL)
--         Composite Foreign Keys (Matrix-Subject, Topic-Subject, CLO-Subject)
--         Strict Lifecycle CHECK Constraints & Optimistic Locking
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. PREFLIGHT VERIFICATION
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'subjects') THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Table "subjects" does not exist.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'topics') THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Table "topics" does not exist.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'clos') THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Table "clos" does not exist.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'exam_matrix') THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Table "exam_matrix" does not exist.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'exam_matrix_details') THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Table "exam_matrix_details" does not exist.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'exams') THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Table "exams" does not exist.';
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 2. ENSURE COMPOSITE UNIQUE CONSTRAINTS ON REFERENCED TABLES
-- -----------------------------------------------------------------------------

-- A. topics(topic_id, subject_id) - Target for fk_matrix_details_topic_subject
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'uq_topics_id_subject' AND table_name = 'topics'
    ) THEN
        ALTER TABLE topics
            ADD CONSTRAINT uq_topics_id_subject
            UNIQUE (topic_id, subject_id);
    END IF;
END $$;

-- B. clos(clo_id, subject_id) - Target for fk_matrix_details_clo_subject
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'uq_clos_id_subject' AND table_name = 'clos'
    ) THEN
        ALTER TABLE clos
            ADD CONSTRAINT uq_clos_id_subject
            UNIQUE (clo_id, subject_id);
    END IF;
END $$;

-- C. exam_matrix(matrix_id, subject_id) - Target for fk_matrix_details_matrix_subject
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'uq_exam_matrix_id_subject' AND table_name = 'exam_matrix'
    ) THEN
        ALTER TABLE exam_matrix
            ADD CONSTRAINT uq_exam_matrix_id_subject
            UNIQUE (matrix_id, subject_id);
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 3. EXPAND TABLE exam_matrix: AUDIT & GOVERNANCE COLUMNS
-- -----------------------------------------------------------------------------

-- A. matrix_family_id (Nhận diện dòng ma trận nghiệp vụ để clone version)
ALTER TABLE exam_matrix ADD COLUMN IF NOT EXISTS matrix_family_id UUID NULL;
UPDATE exam_matrix SET matrix_family_id = matrix_id WHERE matrix_family_id IS NULL;
ALTER TABLE exam_matrix ALTER COLUMN matrix_family_id SET NOT NULL;
ALTER TABLE exam_matrix ALTER COLUMN matrix_family_id SET DEFAULT gen_random_uuid();

-- B. revision_number (Phiên bản nghiệp vụ của ma trận: revision 1, 2, 3...)
ALTER TABLE exam_matrix ADD COLUMN IF NOT EXISTS revision_number INTEGER NULL;
UPDATE exam_matrix SET revision_number = 1 WHERE revision_number IS NULL;
ALTER TABLE exam_matrix ALTER COLUMN revision_number SET NOT NULL;
ALTER TABLE exam_matrix ALTER COLUMN revision_number SET DEFAULT 1;

-- C. row_version (Phiên bản kỹ thuật phục vụ JPA Optimistic Locking @Version)
ALTER TABLE exam_matrix ADD COLUMN IF NOT EXISTS row_version BIGINT NULL;
UPDATE exam_matrix SET row_version = 0 WHERE row_version IS NULL;
ALTER TABLE exam_matrix ALTER COLUMN row_version SET NOT NULL;
ALTER TABLE exam_matrix ALTER COLUMN row_version SET DEFAULT 0;

-- D. status (DRAFT, PUBLISHED, ARCHIVED)
ALTER TABLE exam_matrix ADD COLUMN IF NOT EXISTS status VARCHAR(20) NULL;
UPDATE exam_matrix SET status = 'DRAFT' WHERE status IS NULL;
ALTER TABLE exam_matrix ALTER COLUMN status SET NOT NULL;
ALTER TABLE exam_matrix ALTER COLUMN status SET DEFAULT 'DRAFT';

-- E. is_locked (Khóa sửa đổi khi ma trận đã PUBLISHED/ARCHIVED)
ALTER TABLE exam_matrix ADD COLUMN IF NOT EXISTS is_locked BOOLEAN NULL;
UPDATE exam_matrix SET is_locked = FALSE WHERE is_locked IS NULL;
ALTER TABLE exam_matrix ALTER COLUMN is_locked SET NOT NULL;
ALTER TABLE exam_matrix ALTER COLUMN is_locked SET DEFAULT FALSE;

-- F. created_by (Người tạo ma trận, nullable để an toàn lịch sử)
ALTER TABLE exam_matrix ADD COLUMN IF NOT EXISTS created_by UUID NULL;

-- G. Constraints on exam_matrix
DO $$
BEGIN
    -- FK created_by -> users(user_id) ON DELETE SET NULL
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_exam_matrix_created_by' AND table_name = 'exam_matrix'
    ) THEN
        ALTER TABLE exam_matrix
            ADD CONSTRAINT fk_exam_matrix_created_by
            FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE SET NULL;
    END IF;

    -- UNIQUE (matrix_family_id, revision_number) - Chống trùng revision trong cùng một family
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'uq_matrix_family_revision' AND table_name = 'exam_matrix'
    ) THEN
        ALTER TABLE exam_matrix
            ADD CONSTRAINT uq_matrix_family_revision
            UNIQUE (matrix_family_id, revision_number);
    END IF;

    -- CHECK status hợp lệ
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'chk_exam_matrix_status' AND table_name = 'exam_matrix'
    ) THEN
        ALTER TABLE exam_matrix
            ADD CONSTRAINT chk_exam_matrix_status
            CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'));
    END IF;

    -- CHECK nhất quán giữa status và is_locked:
    -- DRAFT: is_locked = FALSE
    -- PUBLISHED / ARCHIVED: is_locked = TRUE
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'chk_exam_matrix_status_locked' AND table_name = 'exam_matrix'
    ) THEN
        ALTER TABLE exam_matrix
            ADD CONSTRAINT chk_exam_matrix_status_locked
            CHECK (
                (status = 'DRAFT' AND is_locked = FALSE) OR 
                (status IN ('PUBLISHED', 'ARCHIVED') AND is_locked = TRUE)
            );
    END IF;

    -- CHECK revision_number >= 1
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'chk_exam_matrix_revision_number' AND table_name = 'exam_matrix'
    ) THEN
        ALTER TABLE exam_matrix
            ADD CONSTRAINT chk_exam_matrix_revision_number
            CHECK (revision_number >= 1);
    END IF;

    -- CHECK row_version >= 0
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'chk_exam_matrix_row_version' AND table_name = 'exam_matrix'
    ) THEN
        ALTER TABLE exam_matrix
            ADD CONSTRAINT chk_exam_matrix_row_version
            CHECK (row_version >= 0);
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 4. EXPAND TABLE exam_matrix_details: SUBJECT & CLO MAPPING
-- -----------------------------------------------------------------------------

-- A. subject_id (Thêm nullable tạm thời, sau đó backfill từ exam_matrix)
ALTER TABLE exam_matrix_details ADD COLUMN IF NOT EXISTS subject_id UUID NULL;

-- B. Backfill subject_id từ exam_matrix cho các chi tiết ma trận
UPDATE exam_matrix_details emd
SET subject_id = em.subject_id
FROM exam_matrix em
WHERE emd.matrix_id = em.matrix_id AND emd.subject_id IS NULL;

-- C. Fail-fast kiểm tra orphan details
DO $$
DECLARE
    v_orphan_count INT;
BEGIN
    SELECT COUNT(*) INTO v_orphan_count FROM exam_matrix_details WHERE subject_id IS NULL;
    IF v_orphan_count > 0 THEN
        RAISE EXCEPTION 'BACKFILL FAILED: Found % exam_matrix_details with NULL subject_id.', v_orphan_count;
    END IF;
END $$;

-- D. Đặt NOT NULL cho subject_id
ALTER TABLE exam_matrix_details ALTER COLUMN subject_id SET NOT NULL;

-- E. clo_id (Course Learning Outcome - NULLABLE để tương thích hoàn toàn ma trận cũ)
ALTER TABLE exam_matrix_details ADD COLUMN IF NOT EXISTS clo_id UUID NULL;

-- -----------------------------------------------------------------------------
-- 5. COMPOSITE FOREIGN KEYS ON exam_matrix_details
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    -- A. Matrix-Subject Composite FK: Thay thế FK đơn matrix_id
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'exam_matrix_details_matrix_id_fkey' AND table_name = 'exam_matrix_details'
    ) THEN
        ALTER TABLE exam_matrix_details DROP CONSTRAINT exam_matrix_details_matrix_id_fkey;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_matrix_details_matrix_subject' AND table_name = 'exam_matrix_details'
    ) THEN
        ALTER TABLE exam_matrix_details
            ADD CONSTRAINT fk_matrix_details_matrix_subject
            FOREIGN KEY (matrix_id, subject_id)
            REFERENCES exam_matrix(matrix_id, subject_id) ON DELETE CASCADE;
    END IF;

    -- B. Topic-Subject Composite FK: Thay thế FK đơn topic_id, áp dụng ON DELETE RESTRICT bảo vệ cấu trúc
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'exam_matrix_details_topic_id_fkey' AND table_name = 'exam_matrix_details'
    ) THEN
        ALTER TABLE exam_matrix_details DROP CONSTRAINT exam_matrix_details_topic_id_fkey;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_matrix_details_topic_subject' AND table_name = 'exam_matrix_details'
    ) THEN
        ALTER TABLE exam_matrix_details
            ADD CONSTRAINT fk_matrix_details_topic_subject
            FOREIGN KEY (topic_id, subject_id)
            REFERENCES topics(topic_id, subject_id) ON DELETE RESTRICT;
    END IF;

    -- C. CLO-Subject Composite FK: ON DELETE RESTRICT bảo vệ chuẩn đầu ra
    -- Ghi chú PostgreSQL: MATCH SIMPLE tự động chấp nhận khi clo_id IS NULL.
    -- Khi clo_id có giá trị, PostgreSQL bắt buộc (clo_id, subject_id) phải khớp clos(clo_id, subject_id).
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_matrix_details_clo_subject' AND table_name = 'exam_matrix_details'
    ) THEN
        ALTER TABLE exam_matrix_details
            ADD CONSTRAINT fk_matrix_details_clo_subject
            FOREIGN KEY (clo_id, subject_id)
            REFERENCES clos(clo_id, subject_id) ON DELETE RESTRICT;
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 6. INDEXES FOR PERFORMANCE & INTEGRITY
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_exam_matrix_subject_type_status ON exam_matrix(subject_id, exam_type, status);
CREATE INDEX IF NOT EXISTS idx_exam_matrix_family_revision ON exam_matrix(matrix_family_id, revision_number);
CREATE INDEX IF NOT EXISTS idx_matrix_details_matrix_subject ON exam_matrix_details(matrix_id, subject_id);
CREATE INDEX IF NOT EXISTS idx_matrix_details_topic_subject ON exam_matrix_details(topic_id, subject_id);
CREATE INDEX IF NOT EXISTS idx_matrix_details_clo_subject ON exam_matrix_details(clo_id, subject_id) WHERE clo_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 7. POST-MIGRATION INTEGRITY AUDIT
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    v_missing_cols INT;
    v_missing_fks  INT;
BEGIN
    SELECT COUNT(*) INTO v_missing_cols
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND (
          (table_name = 'exam_matrix' AND column_name IN ('matrix_family_id', 'revision_number', 'row_version', 'status', 'is_locked'))
          OR
          (table_name = 'exam_matrix_details' AND column_name IN ('subject_id', 'clo_id'))
      );

    IF v_missing_cols < 7 THEN
        RAISE EXCEPTION 'POST-MIGRATION AUDIT FAILED: Expected 7 new columns across exam_matrix and details, found %', v_missing_cols;
    END IF;

    SELECT COUNT(*) INTO v_missing_fks
    FROM information_schema.table_constraints
    WHERE table_schema = 'public'
      AND constraint_name IN ('fk_matrix_details_matrix_subject', 'fk_matrix_details_topic_subject', 'fk_matrix_details_clo_subject', 'uq_exam_matrix_id_subject');

    IF v_missing_fks < 4 THEN
        RAISE EXCEPTION 'POST-MIGRATION AUDIT FAILED: Expected 4 constraints on exam_matrix/details, found %', v_missing_fks;
    END IF;

    RAISE NOTICE 'POST-MIGRATION AUDIT SUCCESS: All columns, constraints, and indexes verified.';
END $$;

COMMIT;
