-- ============================================================
-- SCHEMA: He thong hoc phan Vat ly 1
-- Database: PostgreSQL 14+
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto; -- gen_random_uuid()

-- ============================================================
-- ENUM TYPES
-- ============================================================
CREATE TYPE user_role              AS ENUM ('student', 'instructor', 'ta', 'admin');
CREATE TYPE user_status            AS ENUM ('active', 'locked');
CREATE TYPE gender_type            AS ENUM ('male', 'female', 'other');
CREATE TYPE class_status           AS ENUM ('draft', 'active', 'completed', 'archived');
CREATE TYPE class_staff_role       AS ENUM ('instructor', 'ta');
CREATE TYPE enrollment_status      AS ENUM ('active', 'dropped', 'completed');
CREATE TYPE material_type          AS ENUM ('pdf', 'video', 'slide', 'text', 'other');
CREATE TYPE approval_status        AS ENUM ('draft', 'pending', 'approved', 'rejected');
CREATE TYPE file_processing_status AS ENUM ('queued', 'processing', 'completed', 'failed');
CREATE TYPE ai_mode                AS ENUM ('text', 'voice');
CREATE TYPE ai_sender              AS ENUM ('user', 'ai');
CREATE TYPE submission_status      AS ENUM ('pending', 'graded', 'confirmed');
CREATE TYPE question_type          AS ENUM ('mcq_single', 'mcq_multi', 'true_false', 'short_answer');
CREATE TYPE difficulty_level       AS ENUM ('easy', 'medium', 'hard');
CREATE TYPE exam_type              AS ENUM ('practice', 'quiz', 'midterm', 'final');
CREATE TYPE attempt_status         AS ENUM ('in_progress', 'submitted', 'graded');
CREATE TYPE progress_status        AS ENUM ('not_started', 'in_progress', 'completed');
CREATE TYPE evidence_source_type   AS ENUM ('experiment', 'exam', 'ai_conversation');

-- ============================================================
-- A. TAI KHOAN & LOP HOC (IAM)
-- ============================================================

-- Bảng 1: users - Thông tin xác thực & phân quyền
CREATE TABLE users (
    user_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      TEXT NOT NULL UNIQUE,
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role          user_role NOT NULL DEFAULT 'student',
    status        user_status NOT NULL DEFAULT 'active',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 1b: refresh_token - Quản lý token phiên đăng nhập
CREATE TABLE refresh_token (
    id            SERIAL PRIMARY KEY,
    token_hash    VARCHAR(64) UNIQUE NOT NULL,
    username      TEXT NOT NULL REFERENCES users(username) ON DELETE CASCADE,
    expiry_date   TIMESTAMPTZ NOT NULL,
    revoked       BOOLEAN NOT NULL DEFAULT FALSE
);

-- Bảng 2: user_profiles - Thông tin cá nhân/hồ sơ sinh viên, giảng viên
CREATE TABLE user_profiles (
    user_id       UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    full_name     TEXT NOT NULL,
    avatar_url    TEXT,
    date_of_birth DATE,
    gender        gender_type,
    phone         TEXT,
    student_code  TEXT,                       -- Mã SV hoặc Mã GV
    bio           TEXT,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 3: subjects - Quản lý môn học (đã bỏ faculty)
CREATE TABLE subjects (
    subject_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_code  TEXT NOT NULL UNIQUE,       -- vd: PHY101
    subject_name  TEXT NOT NULL,              -- vd: Vat ly 1
    description   TEXT,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 4: semesters - Quản lý học kỳ / năm học
CREATE TABLE semesters (
    semester_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    semester_code TEXT NOT NULL UNIQUE,       -- vd: 20251, HK1_2025_2026
    semester_name TEXT NOT NULL,              -- vd: Hoc ky 1
    academic_year TEXT NOT NULL,              -- vd: 2025-2026
    start_date    DATE,
    end_date      DATE,
    is_current    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (semester_name, academic_year)
);

-- Bảng 5: classes - Lớp học phần
CREATE TABLE classes (
    class_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id     UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE RESTRICT,
    semester_id    UUID NOT NULL REFERENCES semesters(semester_id) ON DELETE RESTRICT,
    class_code     TEXT NOT NULL,             -- vd: 01, 02
    instructor_id  UUID REFERENCES users(user_id) ON DELETE SET NULL,
    max_students   INT,
    status         class_status NOT NULL DEFAULT 'active',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (subject_id, semester_id, class_code)
);

-- Bảng 6: class_staff - Phân công GV / Trợ giảng cho lớp
CREATE TABLE class_staff (
    class_id       UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    user_id        UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role_in_class  class_staff_role NOT NULL,
    PRIMARY KEY (class_id, user_id)
);

-- Bảng 7: class_enrollments - Danh sách sinh viên đăng ký lớp
CREATE TABLE class_enrollments (
    enrollment_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id       UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    student_id     UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    enrolled_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    status         enrollment_status NOT NULL DEFAULT 'active',
    UNIQUE (class_id, student_id)
);

-- ============================================================
-- B. QUAN LY FILE UPLOAD & ASYNC PROCESSING (RABBITMQ)
-- ============================================================

-- Bảng 8: file_uploads - Quản lý upload file, hàng đợi RabbitMQ & tiến trình AI embedding/phân tích
CREATE TABLE file_uploads (
    file_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uploader_id       UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    original_filename TEXT NOT NULL,
    stored_url        TEXT NOT NULL,
    file_size_bytes   BIGINT NOT NULL,
    mime_type         TEXT,
    purpose           TEXT NOT NULL,          -- vd: learning_material, lab_evidence, avatar, question_media
    entity_type       TEXT,                   -- vd: learning_materials, experiment_submissions
    entity_id         UUID,                   -- ID bản ghi nghiệp vụ liên kết
    status            file_processing_status NOT NULL DEFAULT 'queued', -- queued (Đang chờ), processing (Đang xử lý/AI embedding), completed (Thành công), failed (Lỗi)
    progress_percent  NUMERIC(5,2) NOT NULL DEFAULT 0,
    error_message     TEXT,
    metadata_json     JSONB,                  -- Lưu chunk_count, vector_ids, queue_job_id, embedding_model
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- C. HOC LIEU
-- ============================================================

-- Bảng 9: topics - Chương / Chủ đề trong môn học
CREATE TABLE topics (
    topic_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id   UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE CASCADE,
    topic_name   TEXT NOT NULL,
    order_index  INT NOT NULL DEFAULT 0,
    description  TEXT
);

-- Bảng 10: learning_materials - Tài liệu bài giảng
CREATE TABLE learning_materials (
    material_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id         UUID NOT NULL REFERENCES topics(topic_id) ON DELETE CASCADE,
    file_id          UUID REFERENCES file_uploads(file_id) ON DELETE SET NULL, -- Liên kết quản lý file & async processing
    title            TEXT NOT NULL,
    type             material_type NOT NULL,
    file_url         TEXT,
    content_text     TEXT,
    version          INT NOT NULL DEFAULT 1,
    approval_status  approval_status NOT NULL DEFAULT 'draft',
    source_citation  TEXT,
    created_by       UUID REFERENCES users(user_id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 11: material_approvals - Lịch sử phê duyệt học liệu
CREATE TABLE material_approvals (
    approval_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    material_id   UUID NOT NULL REFERENCES learning_materials(material_id) ON DELETE CASCADE,
    reviewer_id   UUID NOT NULL REFERENCES users(user_id),
    status        approval_status NOT NULL,
    comment       TEXT,
    reviewed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 12: material_versions - Snapshot các phiên bản học liệu
CREATE TABLE material_versions (
    version_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    material_id       UUID NOT NULL REFERENCES learning_materials(material_id) ON DELETE CASCADE,
    version_no        INT NOT NULL,
    content_snapshot  TEXT,
    changed_by        UUID REFERENCES users(user_id),
    changed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (material_id, version_no)
);

-- ============================================================
-- D. TRO GIANG SOCRATIC AI
-- ============================================================

-- Bảng 13: ai_conversations - Phiên hội thoại với trợ giảng AI
CREATE TABLE ai_conversations (
    conversation_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id       UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    class_id         UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    topic_id         UUID REFERENCES topics(topic_id) ON DELETE SET NULL,
    mode             ai_mode NOT NULL DEFAULT 'text',
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at         TIMESTAMPTZ
);

-- Bảng 14: ai_messages - Chi tiết tin nhắn
CREATE TABLE ai_messages (
    message_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID NOT NULL REFERENCES ai_conversations(conversation_id) ON DELETE CASCADE,
    sender           ai_sender NOT NULL,
    content_text     TEXT,
    audio_url        TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 15: ai_message_citations - Dẫn nguồn trích dẫn từ học liệu
CREATE TABLE ai_message_citations (
    citation_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id       UUID NOT NULL REFERENCES ai_messages(message_id) ON DELETE CASCADE,
    material_id      UUID NOT NULL REFERENCES learning_materials(material_id),
    version_id       UUID REFERENCES material_versions(version_id), -- Đảm bảo trích dẫn đúng phiên bản đã duyệt
    excerpt          TEXT,
    relevance_score  NUMERIC(4,3)
);

-- Bảng 16: ai_refusals - Cơ chế từ chối khi thiếu căn cứ học liệu
CREATE TABLE ai_refusals (
    refusal_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id         UUID NOT NULL REFERENCES ai_messages(message_id) ON DELETE CASCADE,
    reason             TEXT NOT NULL,
    missing_topic_hint TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 17: ai_feedback - Đánh giá của sinh viên về câu trả lời
CREATE TABLE ai_feedback (
    feedback_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id   UUID NOT NULL REFERENCES ai_messages(message_id) ON DELETE CASCADE,
    student_id   UUID NOT NULL REFERENCES users(user_id),
    rating       SMALLINT CHECK (rating BETWEEN 1 AND 5),
    comment      TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- E. THI NGHIEM AO 3D
-- ============================================================

-- Bảng 18: experiments - Danh mục 04 bài thí nghiệm ảo
CREATE TABLE experiments (
    experiment_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id        UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE CASCADE,
    title             TEXT NOT NULL,
    description       TEXT,
    scene_asset_url   TEXT NOT NULL,
    scene_assets_json JSONB,                  -- Lưu danh sách file wasm, data, js nếu là WebGL bundle
    instructions      TEXT,
    order_index       INT NOT NULL DEFAULT 0
);

-- Bảng 19: experiment_rubrics - Tiêu chí chấm điểm rubric
CREATE TABLE experiment_rubrics (
    rubric_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_id  UUID NOT NULL REFERENCES experiments(experiment_id) ON DELETE CASCADE,
    criteria_name  TEXT NOT NULL,
    max_score      NUMERIC(6,2) NOT NULL,
    description    TEXT
);

-- Bảng 20: experiment_assignments - Giao bài thí nghiệm cho lớp
CREATE TABLE experiment_assignments (
    assignment_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_id          UUID NOT NULL REFERENCES experiments(experiment_id) ON DELETE CASCADE,
    class_id               UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    assigned_by            UUID NOT NULL REFERENCES users(user_id),
    due_date               TIMESTAMPTZ,
    instructions_override  TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 21: experiment_submissions - Nộp minh chứng và số liệu
CREATE TABLE experiment_submissions (
    submission_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id   UUID NOT NULL REFERENCES experiment_assignments(assignment_id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    evidence_url    TEXT,
    file_id         UUID REFERENCES file_uploads(file_id) ON DELETE SET NULL, -- File minh chứng tải lên
    raw_data_json   JSONB,                    -- Số liệu đo đạc thực tế từ WebGL 3D
    status          submission_status NOT NULL DEFAULT 'pending',
    UNIQUE (assignment_id, student_id)
);

-- Bảng 22: experiment_scores - Chấm điểm theo rubric (TA/GV chấm)
CREATE TABLE experiment_scores (
    score_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id  UUID NOT NULL REFERENCES experiment_submissions(submission_id) ON DELETE CASCADE,
    rubric_id      UUID NOT NULL REFERENCES experiment_rubrics(rubric_id),
    score          NUMERIC(6,2) NOT NULL,
    grader_id      UUID NOT NULL REFERENCES users(user_id),
    graded_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    comment        TEXT,
    UNIQUE (submission_id, rubric_id)
);

-- Bảng 23: experiment_confirmations - GV xác nhận kết quả cuối cùng (Bước khóa điểm)
CREATE TABLE experiment_confirmations (
    confirmation_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id    UUID NOT NULL UNIQUE REFERENCES experiment_submissions(submission_id) ON DELETE CASCADE,
    instructor_id    UUID NOT NULL REFERENCES users(user_id),
    confirmed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    note             TEXT
);

-- ============================================================
-- F. NGAN HANG CAU HOI & KIEM TRA
-- ============================================================

-- Bảng 24: question_bank - Ngân hàng câu hỏi
CREATE TABLE question_bank (
    question_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id       UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE CASCADE,
    topic_id         UUID NOT NULL REFERENCES topics(topic_id) ON DELETE CASCADE,
    question_type    question_type NOT NULL,
    content          TEXT NOT NULL,
    media_url        TEXT,                      -- Hình ảnh, đồ thị, sơ đồ mạch điện
    difficulty_level difficulty_level NOT NULL,
    cognitive_level  TEXT,                      -- Nho, Hieu, Van dung (Bloom)
    created_by       UUID REFERENCES users(user_id),
    approval_status  approval_status NOT NULL DEFAULT 'pending',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 25: question_options - Các phương án trả lời
CREATE TABLE question_options (
    option_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id  UUID NOT NULL REFERENCES question_bank(question_id) ON DELETE CASCADE,
    option_text  TEXT NOT NULL,
    media_url    TEXT,                          -- Hình ảnh đính kèm đáp án (nếu có)
    is_correct   BOOLEAN NOT NULL DEFAULT FALSE,
    order_index  INT NOT NULL DEFAULT 0
);

-- Bảng 26: exam_matrix - Ma trận đề thi chuẩn hóa
CREATE TABLE exam_matrix (
    matrix_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id   UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE CASCADE,
    exam_type    exam_type NOT NULL,
    description  TEXT
);

-- Bảng 27: exam_matrix_details - Chi tiết số lượng câu theo chủ đề & độ khó
CREATE TABLE exam_matrix_details (
    detail_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    matrix_id         UUID NOT NULL REFERENCES exam_matrix(matrix_id) ON DELETE CASCADE,
    topic_id          UUID NOT NULL REFERENCES topics(topic_id),
    difficulty_level  difficulty_level NOT NULL,
    num_questions     INT NOT NULL,
    weight_percent    NUMERIC(5,2) NOT NULL
);

-- Bảng 28: exams - Đề thi được giao cho lớp
CREATE TABLE exams (
    exam_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id          UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    matrix_id         UUID REFERENCES exam_matrix(matrix_id),
    title             TEXT NOT NULL,
    exam_type         exam_type NOT NULL,
    duration_minutes  INT NOT NULL,
    start_time        TIMESTAMPTZ,
    end_time          TIMESTAMPTZ,
    created_by        UUID NOT NULL REFERENCES users(user_id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 29: exam_questions - Snapshot danh sách câu hỏi trong đề
CREATE TABLE exam_questions (
    exam_id       UUID NOT NULL REFERENCES exams(exam_id) ON DELETE CASCADE,
    question_id   UUID NOT NULL REFERENCES question_bank(question_id),
    order_index   INT NOT NULL DEFAULT 0,
    score_weight  NUMERIC(6,2) NOT NULL DEFAULT 1,
    PRIMARY KEY (exam_id, question_id)
);

-- Bảng 30: exam_attempts - Lần làm bài của sinh viên
CREATE TABLE exam_attempts (
    attempt_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id       UUID NOT NULL REFERENCES exams(exam_id) ON DELETE CASCADE,
    student_id    UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at  TIMESTAMPTZ,
    status        attempt_status NOT NULL DEFAULT 'in_progress',
    total_score   NUMERIC(6,2),
    UNIQUE (exam_id, student_id)
);

-- Bảng 31: exam_answers - Chi tiết câu trả lời của sinh viên (Hỗ trợ cả đơn/nhiều đáp án)
CREATE TABLE exam_answers (
    answer_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id           UUID NOT NULL REFERENCES exam_attempts(attempt_id) ON DELETE CASCADE,
    question_id          UUID NOT NULL REFERENCES question_bank(question_id),
    selected_option_ids  UUID[],               -- Hỗ trợ cả mcq_single và mcq_multi (chọn nhiều đáp án)
    answer_text          TEXT,                 -- Dành cho câu hỏi short_answer
    is_correct           BOOLEAN,
    score                NUMERIC(6,2),
    UNIQUE (attempt_id, question_id)
);

-- ============================================================
-- G. TIEN DO HOC TAP & BANG DIEU KHIEN
-- ============================================================

-- Bảng 32: learning_progress - Tiến độ học tập theo chủ đề
CREATE TABLE learning_progress (
    progress_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id         UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    class_id           UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    topic_id           UUID NOT NULL REFERENCES topics(topic_id) ON DELETE CASCADE,
    status             progress_status NOT NULL DEFAULT 'not_started',
    progress_percent   NUMERIC(5,2) NOT NULL DEFAULT 0,
    last_accessed_at   TIMESTAMPTZ,
    UNIQUE (student_id, class_id, topic_id)
);

-- Bảng 33: activity_logs - Nhật ký hoạt động chi tiết
CREATE TABLE activity_logs (
    log_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    class_id      UUID REFERENCES classes(class_id) ON DELETE SET NULL,
    action_type   TEXT NOT NULL,               -- login, view_material, start_exam, submit_experiment, ai_chat...
    object_type   TEXT,
    object_id     UUID,
    metadata_json JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 34: evidence_repository - Kho hồ sơ minh chứng đánh giá sinh viên
CREATE TABLE evidence_repository (
    evidence_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id   UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    source_type  evidence_source_type NOT NULL,
    source_id    UUID NOT NULL,                -- submission_id / attempt_id / conversation_id
    file_id      UUID REFERENCES file_uploads(file_id) ON DELETE SET NULL,
    file_url     TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 35: dashboard_snapshots - Cache dữ liệu tổng hợp cho bảng điều khiển
CREATE TABLE dashboard_snapshots (
    snapshot_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id      UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    student_id    UUID REFERENCES users(user_id) ON DELETE CASCADE,  -- NULL = toan lop
    period        TEXT NOT NULL,               -- vd: 2026-W35, 2026-08
    data_json     JSONB NOT NULL,
    generated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================
-- H. DU LIEU PHAN TICH TONG HOP
-- ============================================================

-- Bảng 36: topic_difficulty_stats - Thống kê nội dung khó theo chủ đề
CREATE TABLE topic_difficulty_stats (
    stat_id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id                  UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE CASCADE,
    topic_id                    UUID NOT NULL REFERENCES topics(topic_id) ON DELETE CASCADE,
    class_id                    UUID REFERENCES classes(class_id) ON DELETE CASCADE,
    avg_score                   NUMERIC(6,2),
    error_rate                  NUMERIC(5,2),
    common_wrong_options_json   JSONB,
    period                      TEXT NOT NULL,
    generated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 37: question_stats - Thống kê độ khó và độ phân biệt câu hỏi
CREATE TABLE question_stats (
    question_id            UUID PRIMARY KEY REFERENCES question_bank(question_id) ON DELETE CASCADE,
    times_used             INT NOT NULL DEFAULT 0,
    correct_rate           NUMERIC(5,2),
    discrimination_index   NUMERIC(5,2),
    avg_time_seconds       NUMERIC(8,2),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 38: ai_topic_gap_stats - Thống kê lỗ hổng học liệu mà AI hay từ chối
CREATE TABLE ai_topic_gap_stats (
    gap_id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id            UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE CASCADE,
    topic_id              UUID REFERENCES topics(topic_id) ON DELETE CASCADE,
    refusal_count         INT NOT NULL DEFAULT 0,
    frequent_query_sample TEXT,
    period                TEXT NOT NULL,
    generated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 39: material_effectiveness_stats - Đánh giá hiệu quả thực tế của học liệu
CREATE TABLE material_effectiveness_stats (
    material_id                     UUID NOT NULL REFERENCES learning_materials(material_id) ON DELETE CASCADE,
    period                          TEXT NOT NULL,
    view_count                      INT NOT NULL DEFAULT 0,
    avg_time_spent_seconds          NUMERIC(8,2),
    correlated_score_improvement    NUMERIC(6,2),
    PRIMARY KEY (material_id, period)
);

-- ============================================================
-- I. HE THONG & NHAT KY
-- ============================================================

-- Bảng 40: audit_logs - Nhật ký kiểm toán thay đổi dữ liệu
CREATE TABLE audit_logs (
    audit_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID REFERENCES users(user_id) ON DELETE SET NULL,
    action       TEXT NOT NULL,
    entity       TEXT NOT NULL,
    entity_id    UUID,
    old_value    JSONB,
    new_value    JSONB,
    ip_address   INET,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bảng 41: system_settings - Cài đặt cấu hình theo môn / toàn cục
CREATE TABLE system_settings (
    key         TEXT NOT NULL,
    subject_id  UUID REFERENCES subjects(subject_id) ON DELETE CASCADE,  -- NULL = global
    value       JSONB NOT NULL,
    PRIMARY KEY (key, subject_id)
);

-- ============================================================
-- INDEXES BO SUNG (FK va truy van thuong dung)
-- ============================================================

CREATE UNIQUE INDEX idx_one_current_semester ON semesters(is_current) WHERE is_current = true;
CREATE INDEX idx_classes_subject            ON classes(subject_id);
CREATE INDEX idx_classes_semester           ON classes(semester_id);
CREATE INDEX idx_enrollments_student        ON class_enrollments(student_id);
CREATE INDEX idx_file_uploads_uploader      ON file_uploads(uploader_id);
CREATE INDEX idx_file_uploads_status        ON file_uploads(status);
CREATE INDEX idx_topics_subject             ON topics(subject_id);
CREATE INDEX idx_materials_topic            ON learning_materials(topic_id);
CREATE INDEX idx_materials_approval_status  ON learning_materials(approval_status);
CREATE INDEX idx_ai_conversations_student   ON ai_conversations(student_id);
CREATE INDEX idx_ai_messages_conversation   ON ai_messages(conversation_id);
CREATE INDEX idx_ai_citations_message       ON ai_message_citations(message_id);
CREATE INDEX idx_experiments_subject        ON experiments(subject_id);
CREATE INDEX idx_exp_assignments_class      ON experiment_assignments(class_id);
CREATE INDEX idx_exp_submissions_student    ON experiment_submissions(student_id);
CREATE INDEX idx_question_bank_topic        ON question_bank(topic_id);
CREATE INDEX idx_exams_class                ON exams(class_id);
CREATE INDEX idx_exam_attempts_student      ON exam_attempts(student_id);
CREATE INDEX idx_exam_answers_attempt       ON exam_answers(attempt_id);
CREATE INDEX idx_progress_student_class     ON learning_progress(student_id, class_id);
CREATE INDEX idx_activity_logs_user         ON activity_logs(user_id);
CREATE INDEX idx_activity_logs_created_at   ON activity_logs(created_at);
CREATE INDEX idx_evidence_student           ON evidence_repository(student_id);
CREATE INDEX idx_topic_diff_stats_topic     ON topic_difficulty_stats(topic_id);

-- ------------------------------------------------------------
-- 42. PASSWORD RESET TOKENS (Token khoi phuc mat khau qua email)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    token_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token        VARCHAR(100) NOT NULL UNIQUE,
    user_id      UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    email        VARCHAR(255) NOT NULL,
    expiry_date  TIMESTAMPTZ NOT NULL,
    used         BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pwd_reset_token   ON password_reset_tokens(token);
CREATE INDEX idx_pwd_reset_user_id ON password_reset_tokens(user_id);

-- ============================================================
-- HET
-- ============================================================
