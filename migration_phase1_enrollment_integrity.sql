-- =============================================================================
-- PHASE 1: DATABASE MIGRATION SCRIPT - ENROLLMENT INTEGRITY (PRODUCTION READY)
-- Target: PostgreSQL 14+ / 16 / 17 / 18
-- Mục tiêu: Đảm bảo toàn vẹn dữ liệu ghi danh. Ngăn sinh viên đăng ký đồng thời 
--           vào hai lớp khác nhau của cùng một môn học trong cùng học kỳ (Chỉ cho phép 1 enrollment ACTIVE).
-- An toàn: Transactional Atomic Migration, Active Preflight Blockers, Idempotent DDL,
--          Chuẩn hóa biểu diễn kỹ thuật (Technical Representation Normalization),
--          Chống che giấu Index sai định nghĩa, Khuyến nghị Maintenance Window.
-- =============================================================================

-- Thiết lập psql dừng ngay lập tức khi gặp lỗi và trả về exit code lỗi cho OS/CI-CD:
\set ON_ERROR_STOP on

BEGIN;

-- =============================================================================
-- BƯỚC 1: PREFLIGHT AUDIT & CONFLICT DETECTION (ACTIVE BLOCKING)
-- =============================================================================
-- Lưu ý: Nếu có bất kỳ vi phạm nào, khối DO sẽ ném EXCEPTION và rollback ngay lập tức.
-- Không có bất kỳ DDL nào được thực thi nếu dữ liệu còn xung đột.

-- 1.1. Chặn migration nếu phát hiện dữ liệu đăng ký ACTIVE bị trùng lặp
DO $$
DECLARE
    conflict_count INT;
BEGIN
    SELECT COUNT(*) INTO conflict_count
    FROM (
        SELECT ce.student_id, c.subject_id, c.semester_id
        FROM class_enrollments ce
        JOIN classes c ON ce.class_id = c.class_id
        WHERE UPPER(CAST(ce.status AS TEXT)) = 'ACTIVE'
        GROUP BY ce.student_id, c.subject_id, c.semester_id
        HAVING COUNT(*) > 1
    ) conflicts;

    IF conflict_count > 0 THEN
        RAISE EXCEPTION 'PREFLIGHT AUDIT FAILED: Phát hiện % trường hợp sinh viên có từ 2 đăng ký ACTIVE trở lên trong cùng môn và học kỳ. Migration bị dừng để bảo vệ toàn vẹn dữ liệu!', conflict_count;
    END IF;
    RAISE NOTICE 'Preflight Audit 1: Không có xung đột đăng ký ACTIVE.';
END $$;

-- 1.2. Chặn migration nếu có bản ghi enrollment mồ côi (class_id không tồn tại trong classes)
DO $$
DECLARE
    orphaned_count INT;
BEGIN
    SELECT COUNT(*) INTO orphaned_count
    FROM class_enrollments ce 
    LEFT JOIN classes c ON ce.class_id = c.class_id 
    WHERE c.class_id IS NULL;

    IF orphaned_count > 0 THEN
        RAISE EXCEPTION 'PREFLIGHT AUDIT FAILED: Phát hiện % bản ghi class_enrollments mồ côi (class_id không hợp lệ). Migration bị hủy bỏ!', orphaned_count;
    END IF;
    RAISE NOTICE 'Preflight Audit 2: Không có bản ghi enrollment mồ côi.';
END $$;


-- =============================================================================
-- BƯỚC 2: DDL & IDEMPOTENT BACKFILL
-- =============================================================================

-- 2.1. Thêm ràng buộc UNIQUE composite trên bảng classes (cần cho Composite FK)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_classes_id_subject_semester'
    ) THEN
        ALTER TABLE classes 
            ADD CONSTRAINT uq_classes_id_subject_semester 
            UNIQUE (class_id, subject_id, semester_id);
        RAISE NOTICE 'Đã tạo constraint uq_classes_id_subject_semester trên classes.';
    ELSE
        RAISE NOTICE 'Constraint uq_classes_id_subject_semester đã tồn tại.';
    END IF;
END $$;

-- 2.2. Bổ sung các cột subject_id, semester_id vào class_enrollments (cho phép NULL tạm thời)
ALTER TABLE class_enrollments ADD COLUMN IF NOT EXISTS subject_id UUID;
ALTER TABLE class_enrollments ADD COLUMN IF NOT EXISTS semester_id UUID;

-- 2.3. Chuyển đổi an toàn kiểu cột status từ ENUM sang VARCHAR(50) nếu database cũ dùng PostgreSQL custom enum
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'class_enrollments' 
          AND column_name = 'status' 
          AND udt_name = 'enrollment_status'
    ) THEN
        ALTER TABLE class_enrollments ALTER COLUMN status DROP DEFAULT;
        ALTER TABLE class_enrollments ALTER COLUMN status TYPE VARCHAR(50) USING status::text;
        ALTER TABLE class_enrollments ALTER COLUMN status SET DEFAULT 'ACTIVE';
        RAISE NOTICE 'Đã chuyển đổi kiểu cột status từ enrollment_status sang VARCHAR(50) an toàn.';
    ELSE
        -- Đảm bảo default value chuẩn xác
        ALTER TABLE class_enrollments ALTER COLUMN status SET DEFAULT 'ACTIVE';
    END IF;
END $$;

-- 2.4. Chuẩn hóa biểu diễn kỹ thuật (Technical Representation Normalization):
-- Đồng bộ hóa cách viết hoa/thường (Casing) trong dữ liệu sang UPPERCASE chuẩn của hệ thống.
-- LƯU Ý QUAN TRỌNG: Đây là thay đổi biểu diễn kỹ thuật để đồng bộ với Hibernate/JPA,
-- TUYỆT ĐỐI KHÔNG thay đổi trạng thái nghiệp vụ của sinh viên (không chuyển đổi giữa active, dropped, completed).
UPDATE class_enrollments SET status = 'ACTIVE' WHERE status = 'active';
UPDATE class_enrollments SET status = 'DROPPED' WHERE status = 'dropped';
UPDATE class_enrollments SET status = 'COMPLETED' WHERE status = 'completed';

-- 2.4b. Bổ sung CHECK Constraint đảm bảo status chỉ nhận các giá trị hợp lệ: ACTIVE, DROPPED, COMPLETED
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_class_enrollment_status'
    ) THEN
        ALTER TABLE class_enrollments 
            ADD CONSTRAINT chk_class_enrollment_status 
            CHECK (status IN ('ACTIVE', 'DROPPED', 'COMPLETED'));
        RAISE NOTICE 'Đã tạo CHECK Constraint chk_class_enrollment_status.';
    ELSE
        RAISE NOTICE 'CHECK Constraint chk_class_enrollment_status đã tồn tại.';
    END IF;
END $$;

-- 2.5. Kiểm tra trước backfill: Đảm bảo không có bản ghi đã có subject_id/semester_id nhưng sai lệch với class
DO $$
DECLARE
    mismatch_count INT;
BEGIN
    SELECT COUNT(*) INTO mismatch_count
    FROM class_enrollments ce
    JOIN classes c ON ce.class_id = c.class_id
    WHERE (ce.subject_id IS NOT NULL AND ce.subject_id != c.subject_id)
       OR (ce.semester_id IS NOT NULL AND ce.semester_id != c.semester_id);

    IF mismatch_count > 0 THEN
        RAISE EXCEPTION 'Backfill thất bại: Phát hiện % bản ghi enrollment có subject_id/semester_id không khớp với thông tin lớp học!', mismatch_count;
    END IF;
    RAISE NOTICE 'Kiểm tra dữ liệu backfill: Không có sai lệch thông tin.';
END $$;

-- 2.6. Backfill dữ liệu subject_id, semester_id từ bảng classes sang class_enrollments
UPDATE class_enrollments ce
SET subject_id = c.subject_id,
    semester_id = c.semester_id
FROM classes c
WHERE ce.class_id = c.class_id
  AND (ce.subject_id IS NULL OR ce.semester_id IS NULL);

-- 2.7. Xác nhận không còn bản ghi nào có subject_id hoặc semester_id bị NULL
DO $$
DECLARE
    null_count INT;
BEGIN
    SELECT COUNT(*) INTO null_count 
    FROM class_enrollments 
    WHERE subject_id IS NULL OR semester_id IS NULL;
    
    IF null_count > 0 THEN
        RAISE EXCEPTION 'Backfill thất bại: Còn % bản ghi enrollment có subject_id/semester_id bị NULL', null_count;
    END IF;
    RAISE NOTICE 'Xác nhận backfill: Tất cả bản ghi đã được điền subject_id và semester_id.';
END $$;

-- 2.8. Đặt NOT NULL cho các cột mới
ALTER TABLE class_enrollments ALTER COLUMN subject_id SET NOT NULL;
ALTER TABLE class_enrollments ALTER COLUMN semester_id SET NOT NULL;

-- 2.9. Bổ sung Composite Foreign Key từ class_enrollments tới classes (ON DELETE CASCADE)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_enrollment_class_subject_semester'
    ) THEN
        ALTER TABLE class_enrollments 
            ADD CONSTRAINT fk_enrollment_class_subject_semester 
            FOREIGN KEY (class_id, subject_id, semester_id) 
            REFERENCES classes (class_id, subject_id, semester_id) 
            ON DELETE CASCADE;
        RAISE NOTICE 'Đã tạo Composite Foreign Key fk_enrollment_class_subject_semester.';
    ELSE
        RAISE NOTICE 'Constraint fk_enrollment_class_subject_semester đã tồn tại.';
    END IF;
END $$;

-- 2.10. Bổ sung Foreign Key trực tiếp tới subjects và semesters (ON DELETE RESTRICT)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_enrollment_subject'
    ) THEN
        ALTER TABLE class_enrollments 
            ADD CONSTRAINT fk_enrollment_subject 
            FOREIGN KEY (subject_id) 
            REFERENCES subjects (subject_id) 
            ON DELETE RESTRICT;
        RAISE NOTICE 'Đã tạo Foreign Key fk_enrollment_subject.';
    ELSE
        RAISE NOTICE 'Constraint fk_enrollment_subject đã tồn tại.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_enrollment_semester'
    ) THEN
        ALTER TABLE class_enrollments 
            ADD CONSTRAINT fk_enrollment_semester 
            FOREIGN KEY (semester_id) 
            REFERENCES semesters (semester_id) 
            ON DELETE RESTRICT;
        RAISE NOTICE 'Đã tạo Foreign Key fk_enrollment_semester.';
    ELSE
        RAISE NOTICE 'Constraint fk_enrollment_semester đã tồn tại.';
    END IF;
END $$;

-- 2.11. Tạo Partial Unique Index có kiểm tra tính hợp lệ của index đã tồn tại
-- (Ngăn chặn che giấu index cùng tên nhưng sai định nghĩa)
DO $$
DECLARE
    existing_indexdef TEXT;
BEGIN
    SELECT indexdef INTO existing_indexdef
    FROM pg_indexes
    WHERE tablename = 'class_enrollments'
      AND indexname = 'uq_active_enrollment_subject_semester';

    IF existing_indexdef IS NOT NULL THEN
        -- Kiểm tra xem index hiện tại có đúng các cột và điều kiện status ACTIVE không
        IF NOT (
            existing_indexdef ILIKE '%student_id%' AND
            existing_indexdef ILIKE '%subject_id%' AND
            existing_indexdef ILIKE '%semester_id%' AND
            existing_indexdef ILIKE '%WHERE%' AND
            existing_indexdef ILIKE '%status%' AND
            existing_indexdef ILIKE '%ACTIVE%'
        ) THEN
            RAISE EXCEPTION 'Index uq_active_enrollment_subject_semester đã tồn tại nhưng sai định nghĩa: %. Vui lòng xử lý thủ công trước khi tiếp tục.', existing_indexdef;
        ELSE
            RAISE NOTICE 'Index uq_active_enrollment_subject_semester hợp lệ đã tồn tại.';
        END IF;
    ELSE
        CREATE UNIQUE INDEX uq_active_enrollment_subject_semester
        ON class_enrollments (student_id, subject_id, semester_id)
        WHERE status = 'ACTIVE';
        RAISE NOTICE 'Đã tạo Partial Unique Index uq_active_enrollment_subject_semester thành công.';
    END IF;
END $$;

COMMIT;

-- =============================================================================
-- BƯỚC 3: POST-MIGRATION VERIFICATION (DBA KIỂM CHỨNG)
-- =============================================================================
SELECT conname, contype 
FROM pg_constraint 
WHERE conname IN (
    'uq_classes_id_subject_semester',
    'fk_enrollment_class_subject_semester',
    'fk_enrollment_subject',
    'fk_enrollment_semester',
    'chk_class_enrollment_status'
);

SELECT indexname, indexdef 
FROM pg_indexes 
WHERE indexname = 'uq_active_enrollment_subject_semester';


-- =============================================================================
-- BƯỚC 4: ROLLBACK SCRIPT (PHƯƠNG ÁN KHÔI PHỤC KHI CẦN THIẾT - CHẠY THỦ CÔNG)
-- =============================================================================
/*
BEGIN;

-- 1. Hủy Partial Unique Index
DROP INDEX IF EXISTS uq_active_enrollment_subject_semester;

-- 2. Hủy các Foreign Key và CHECK Constraint mới
ALTER TABLE class_enrollments DROP CONSTRAINT IF EXISTS fk_enrollment_class_subject_semester;
ALTER TABLE class_enrollments DROP CONSTRAINT IF EXISTS fk_enrollment_subject;
ALTER TABLE class_enrollments DROP CONSTRAINT IF EXISTS fk_enrollment_semester;
ALTER TABLE class_enrollments DROP CONSTRAINT IF EXISTS chk_class_enrollment_status;

-- 3. Hủy Composite Unique Key trên classes
ALTER TABLE classes DROP CONSTRAINT IF EXISTS uq_classes_id_subject_semester;

-- 4. Chú ý: Trên môi trường Production, KHÔNG DROP cột subject_id, semester_id
-- nếu ứng dụng đang chạy đã bắt đầu ghi dữ liệu vào các cột này!
-- Chỉ thực hiện DROP COLUMN trên môi trường test/staging nếu thực sự cần revert schema hoàn toàn:
-- ALTER TABLE class_enrollments DROP COLUMN IF EXISTS subject_id;
-- ALTER TABLE class_enrollments DROP COLUMN IF EXISTS semester_id;

COMMIT;
*/
