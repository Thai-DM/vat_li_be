-- =============================================================================
-- Migration: Exam Proctoring Modes, Violation Logging & Disqualification (Ban)
-- =============================================================================

-- 1. Cập nhật CHECK constraint trên bảng exam_attempts cho cột status (hỗ trợ DISQUALIFIED)
ALTER TABLE exam_attempts DROP CONSTRAINT IF EXISTS exam_attempts_status_check;
ALTER TABLE exam_attempts ADD CONSTRAINT exam_attempts_status_check 
    CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'GRADED', 'DISQUALIFIED'));

-- Nếu DB dùng kiểu ENUM (pg_type attempt_status)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'attempt_status') THEN
        BEGIN
            ALTER TYPE attempt_status ADD VALUE IF NOT EXISTS 'disqualified';
        EXCEPTION WHEN duplicate_object THEN NULL;
        END;
        BEGIN
            ALTER TYPE attempt_status ADD VALUE IF NOT EXISTS 'DISQUALIFIED';
        EXCEPTION WHEN duplicate_object THEN NULL;
        END;
    END IF;
END $$;

-- 2. Thêm cấu hình giám sát vào bảng exams
ALTER TABLE exams
    ADD COLUMN IF NOT EXISTS proctoring_mode VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS max_violations_allowed INTEGER DEFAULT 5,
    ADD COLUMN IF NOT EXISTS auto_ban_on_limit BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Thêm bộ đếm và trạng thái cấm thi vào bảng exam_attempts
ALTER TABLE exam_attempts
    ADD COLUMN IF NOT EXISTS violation_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS is_disqualified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS disqualified_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS disqualified_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS disqualified_by UUID;

-- 4. Ràng buộc khóa ngoại disqualified_by tham chiếu users(user_id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_exam_attempts_disqualified_by'
    ) THEN
        ALTER TABLE exam_attempts
        ADD CONSTRAINT fk_exam_attempts_disqualified_by
        FOREIGN KEY (disqualified_by) REFERENCES users(user_id) ON DELETE SET NULL;
    END IF;
END $$;

-- 5. Tạo bảng lưu chi tiết lịch sử vi phạm quy chế thi
CREATE TABLE IF NOT EXISTS exam_violation_logs (
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id UUID NOT NULL REFERENCES exam_attempts(attempt_id) ON DELETE CASCADE,
    exam_id UUID NOT NULL REFERENCES exams(exam_id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    violation_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'LOW',
    violation_count_at_time INTEGER NOT NULL DEFAULT 1,
    description VARCHAR(500),
    metadata_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. Tạo chỉ mục tối ưu truy vấn
CREATE INDEX IF NOT EXISTS idx_violation_attempt_id ON exam_violation_logs(attempt_id);
CREATE INDEX IF NOT EXISTS idx_violation_exam_id ON exam_violation_logs(exam_id);
CREATE INDEX IF NOT EXISTS idx_violation_student_id ON exam_violation_logs(student_id);
