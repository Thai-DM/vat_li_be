-- ============================================================================
-- MIGRATION: Phase 5 Step 2.1 - Exam Matrix Delete Safety & Integrity Correction
-- Mục tiêu:
--   1. Chuyển composite FK fk_matrix_details_matrix_subject từ ON DELETE CASCADE sang ON DELETE RESTRICT.
--   2. Rà soát và loại bỏ/chuẩn hóa bất kỳ FK đơn cũ nào từ exam_matrix_details sang exam_matrix có CASCADE.
--   3. Bảo vệ dữ liệu lịch sử của MatrixDetail khi xóa Matrix.
--   4. Kiểm tra fail-fast, orphan checks và hậu kiểm pg_catalog.
-- Cơ sở dữ liệu: Neon PostgreSQL (Test Database)
-- ============================================================================

DO $$
BEGIN
    RAISE NOTICE '>>> BẮT ĐẦU MIGRATION PHASE 5 STEP 2.1: EXAM MATRIX DELETE SAFETY <<<';
END $$;

BEGIN;

-- 1. PREFLIGHT CHECKS: Xác minh các bảng liên quan tồn tại
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'exam_matrix') THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Table "exam_matrix" does not exist.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'exam_matrix_details') THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Table "exam_matrix_details" does not exist.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'exam_matrix'::regclass
          AND conname = 'uq_exam_matrix_id_subject'
    ) THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Unique constraint "uq_exam_matrix_id_subject" on exam_matrix does not exist.';
    END IF;

    RAISE NOTICE 'Preflight checks passed: Tables and unique constraints verified.';
END $$;

-- 2. ORPHAN INTEGRITY CHECK: Đảm bảo không có bản ghi mồ côi trước khi thao tác
DO $$
DECLARE
    v_orphan_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_orphan_count
    FROM exam_matrix_details d
    LEFT JOIN exam_matrix m ON d.matrix_id = m.matrix_id AND d.subject_id = m.subject_id
    WHERE m.matrix_id IS NULL;

    IF v_orphan_count > 0 THEN
        RAISE EXCEPTION 'INTEGRITY CHECK FAILED: Found % orphan exam_matrix_details records without matching exam_matrix(matrix_id, subject_id).', v_orphan_count;
    END IF;

    RAISE NOTICE 'Orphan integrity check passed: No orphan details found.';
END $$;

-- 3. XÓA BỎ / CHUẨN HÓA CÁC FK CŨ CÓ THỂ GÂY XUNG ĐỘT HOẶC CÓ CASCADE
-- Kiểm tra nếu tồn tại FK đơn cũ exam_matrix_details_matrix_id_fkey
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'exam_matrix_details'::regclass
          AND conname = 'exam_matrix_details_matrix_id_fkey'
    ) THEN
        ALTER TABLE exam_matrix_details DROP CONSTRAINT exam_matrix_details_matrix_id_fkey;
        RAISE NOTICE 'Dropped legacy single FK: exam_matrix_details_matrix_id_fkey';
    END IF;
END $$;

-- 4. THAY THẾ COMPOSITE FK: TỪ CASCADE SANG RESTRICT
DO $$
DECLARE
    v_current_deltype "char";
BEGIN
    -- Kiểm tra delete rule hiện tại của fk_matrix_details_matrix_subject nếu đã tồn tại
    SELECT confdeltype INTO v_current_deltype
    FROM pg_constraint
    WHERE conrelid = 'exam_matrix_details'::regclass
      AND conname = 'fk_matrix_details_matrix_subject';

    IF FOUND THEN
        IF v_current_deltype = 'r' THEN
            RAISE NOTICE 'fk_matrix_details_matrix_subject is already ON DELETE RESTRICT. No recreation needed.';
        ELSE
            RAISE NOTICE 'Current fk_matrix_details_matrix_subject confdeltype is "%". Recreating with RESTRICT...', v_current_deltype;
            ALTER TABLE exam_matrix_details DROP CONSTRAINT fk_matrix_details_matrix_subject;
            ALTER TABLE exam_matrix_details
                ADD CONSTRAINT fk_matrix_details_matrix_subject
                FOREIGN KEY (matrix_id, subject_id)
                REFERENCES exam_matrix(matrix_id, subject_id)
                ON DELETE RESTRICT;
            RAISE NOTICE 'fk_matrix_details_matrix_subject recreated successfully with ON DELETE RESTRICT.';
        END IF;
    ELSE
        ALTER TABLE exam_matrix_details
            ADD CONSTRAINT fk_matrix_details_matrix_subject
            FOREIGN KEY (matrix_id, subject_id)
            REFERENCES exam_matrix(matrix_id, subject_id)
            ON DELETE RESTRICT;
        RAISE NOTICE 'fk_matrix_details_matrix_subject created with ON DELETE RESTRICT.';
    END IF;
END $$;

-- 5. HẬU KIỂM POST-MIGRATION BẰNG PG_CATALOG
DO $$
DECLARE
    v_cascade_count INTEGER;
    v_rule "char";
BEGIN
    -- Kiểm tra fk_matrix_details_matrix_subject có đúng rule 'r' (RESTRICT) không
    SELECT confdeltype INTO v_rule
    FROM pg_constraint
    WHERE conrelid = 'exam_matrix_details'::regclass
      AND conname = 'fk_matrix_details_matrix_subject';

    IF v_rule IS NULL OR v_rule <> 'r' THEN
        RAISE EXCEPTION 'POST-MIGRATION FAILED: fk_matrix_details_matrix_subject confdeltype is "%", expected "r" (RESTRICT).', v_rule;
    END IF;

    -- Kiểm tra tuyệt đối không còn bất kỳ FK nào từ exam_matrix_details tham chiếu exam_matrix có rule 'c' (CASCADE)
    SELECT COUNT(*) INTO v_cascade_count
    FROM pg_constraint
    WHERE conrelid = 'exam_matrix_details'::regclass
      AND confrelid = 'exam_matrix'::regclass
      AND confdeltype = 'c';

    IF v_cascade_count > 0 THEN
        RAISE EXCEPTION 'POST-MIGRATION FAILED: Found % FKs with ON DELETE CASCADE referencing exam_matrix on exam_matrix_details.', v_cascade_count;
    END IF;

    RAISE NOTICE 'Post-migration checks passed: fk_matrix_details_matrix_subject verified as RESTRICT. Zero CASCADE FKs.';
END $$;

COMMIT;

DO $$
BEGIN
    RAISE NOTICE '>>> HOÀN THÀNH MIGRATION PHASE 5 STEP 2.1: EXAM MATRIX DELETE SAFETY THÀNH CÔNG <<<';
END $$;
