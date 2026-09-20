-- =============================================================================
-- HE THONG QUAN LY HOC TAP & THI NGHIEM AO VAT LY 1 (BACKEND)
-- DATABASE INITIALIZATION SCRIPT (DDL + SEED DATA)
-- Database Engine: PostgreSQL 14+ / 16
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto; -- gen_random_uuid()

-- =============================================================================
-- PHAN 1: TAO BANG DU LIEU (DDL)
-- =============================================================================

-- 1. users - Tai khoan xac thuc & phan quyen
CREATE TABLE IF NOT EXISTS users (
    user_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(100) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50) NOT NULL DEFAULT 'STUDENT',
    status        VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. refresh_token - Quan ly token phien dang nhap xoay vong
CREATE TABLE IF NOT EXISTS refresh_token (
    id            SERIAL PRIMARY KEY,
    token_hash    VARCHAR(64) UNIQUE NOT NULL,
    username      VARCHAR(100) NOT NULL REFERENCES users(username) ON DELETE CASCADE,
    expiry_date   TIMESTAMPTZ NOT NULL,
    revoked       BOOLEAN NOT NULL DEFAULT FALSE
);

-- 3. user_profiles - Thong tin ca nhan ho so sinh vien / giang vien
CREATE TABLE IF NOT EXISTS user_profiles (
    user_id       UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    full_name     VARCHAR(255) NOT NULL,
    avatar_url    TEXT,
    date_of_birth DATE,
    gender        VARCHAR(20),
    phone         VARCHAR(50),
    student_code  VARCHAR(50),
    bio           TEXT,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. subjects - Danh muc mon hoc
CREATE TABLE IF NOT EXISTS subjects (
    subject_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_code  VARCHAR(50) NOT NULL UNIQUE,
    subject_name  VARCHAR(255) NOT NULL,
    description   TEXT,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. semesters - Danh muc hoc ky / nam hoc
CREATE TABLE IF NOT EXISTS semesters (
    semester_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    semester_code VARCHAR(50) NOT NULL UNIQUE,
    semester_name VARCHAR(100) NOT NULL,
    academic_year VARCHAR(50) NOT NULL,
    start_date    DATE,
    end_date      DATE,
    is_current    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (semester_name, academic_year)
);

-- 6. classes - Lop hoc phan
CREATE TABLE IF NOT EXISTS classes (
    class_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id     UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE RESTRICT,
    semester_id    UUID NOT NULL REFERENCES semesters(semester_id) ON DELETE RESTRICT,
    class_code     VARCHAR(50) NOT NULL,
    instructor_id  UUID REFERENCES users(user_id) ON DELETE SET NULL,
    max_students   INT,
    status         VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (subject_id, semester_id, class_code)
);

-- 7. class_staff - Phan cong tro giang / giang vien dong giang
CREATE TABLE IF NOT EXISTS class_staff (
    class_id      UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    role          VARCHAR(50) NOT NULL DEFAULT 'INSTRUCTOR',
    assigned_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (class_id, user_id)
);

-- 8. class_enrollments - Danh sach sinh vien ghi danh vao lop
CREATE TABLE IF NOT EXISTS class_enrollments (
    enrollment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id      UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    student_id    UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    enrolled_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status        VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    final_grade   NUMERIC(4,2),
    UNIQUE (class_id, student_id)
);

-- 9. file_uploads - Nhat ky luu tru tep tin va anh (MinIO / Storage)
CREATE TABLE IF NOT EXISTS file_uploads (
    file_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uploader_id   UUID REFERENCES users(user_id) ON DELETE SET NULL,
    file_name     VARCHAR(255) NOT NULL,
    file_path     TEXT NOT NULL,
    file_url      TEXT,
    mime_type     VARCHAR(100),
    file_size     BIGINT,
    checksum      VARCHAR(64),
    status        VARCHAR(50) NOT NULL DEFAULT 'COMPLETED',
    error_message TEXT,
    uploaded_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 10. topics - Chu de bai hoc theo chuong trinh
CREATE TABLE IF NOT EXISTS topics (
    topic_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id    UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE CASCADE,
    topic_code    VARCHAR(50) NOT NULL,
    topic_name    VARCHAR(255) NOT NULL,
    description   TEXT,
    order_index   INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (subject_id, topic_code)
);

-- 11. learning_materials - Tai lieu hoc tap (slide, pdf, video, text)
CREATE TABLE IF NOT EXISTS learning_materials (
    material_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id         UUID NOT NULL REFERENCES topics(topic_id) ON DELETE CASCADE,
    title            VARCHAR(255) NOT NULL,
    type             VARCHAR(50) NOT NULL DEFAULT 'PDF',
    content_url      TEXT,
    metadata_json    JSONB,
    current_version  INT NOT NULL DEFAULT 1,
    approval_status  VARCHAR(50) NOT NULL DEFAULT 'APPROVED',
    created_by       UUID REFERENCES users(user_id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 12. material_versions - Lich su phien ban tai lieu
CREATE TABLE IF NOT EXISTS material_versions (
    version_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    material_id   UUID NOT NULL REFERENCES learning_materials(material_id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    content_url   TEXT NOT NULL,
    changelog     TEXT,
    created_by    UUID REFERENCES users(user_id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (material_id, version_number)
);

-- 13. material_approvals - Phe duyet xuat ban tai lieu
CREATE TABLE IF NOT EXISTS material_approvals (
    approval_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    material_id   UUID NOT NULL REFERENCES learning_materials(material_id) ON DELETE CASCADE,
    version_id    UUID NOT NULL REFERENCES material_versions(version_id) ON DELETE CASCADE,
    approver_id   UUID REFERENCES users(user_id) ON DELETE SET NULL,
    status        VARCHAR(50) NOT NULL DEFAULT 'APPROVED',
    comment       TEXT,
    decided_at    TIMESTAMPTZ
);

-- 14. ai_conversations - Phien tro chuyen voi Tro ly AI
CREATE TABLE IF NOT EXISTS ai_conversations (
    conversation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    topic_id        UUID REFERENCES topics(topic_id) ON DELETE SET NULL,
    mode            VARCHAR(50) NOT NULL DEFAULT 'TEXT',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at        TIMESTAMPTZ
);

-- 15. ai_messages - Tin nhan trong phien AI
CREATE TABLE IF NOT EXISTS ai_messages (
    message_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversations(conversation_id) ON DELETE CASCADE,
    sender          VARCHAR(50) NOT NULL DEFAULT 'USER',
    content         TEXT NOT NULL,
    audio_url       TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 16. ai_message_citations - Trich dan tai lieu trong phan hoi AI
CREATE TABLE IF NOT EXISTS ai_message_citations (
    citation_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id     UUID NOT NULL REFERENCES ai_messages(message_id) ON DELETE CASCADE,
    material_id    UUID REFERENCES learning_materials(material_id) ON DELETE SET NULL,
    quote_text     TEXT,
    score          NUMERIC(4,3)
);

-- 17. ai_feedbacks - Danh gia phan hoi cua AI tu sinh vien
CREATE TABLE IF NOT EXISTS ai_feedbacks (
    feedback_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id      UUID NOT NULL UNIQUE REFERENCES ai_messages(message_id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    rating          INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 18. ai_refusals - Ghi nhan cau hoi AI tu choi de phat hien lo hong
CREATE TABLE IF NOT EXISTS ai_refusals (
    refusal_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID REFERENCES ai_conversations(conversation_id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    topic_id        UUID REFERENCES topics(topic_id) ON DELETE SET NULL,
    query_text      TEXT NOT NULL,
    refusal_reason  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 19. experiments - Danh muc bai thi nghiem ao Vat ly 1
CREATE TABLE IF NOT EXISTS experiments (
    experiment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id    UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE CASCADE,
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    instruction   TEXT,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 20. experiment_rubrics - Tieu chi cham diem thi nghiem
CREATE TABLE IF NOT EXISTS experiment_rubrics (
    rubric_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_id UUID NOT NULL REFERENCES experiments(experiment_id) ON DELETE CASCADE,
    criteria_name VARCHAR(255) NOT NULL,
    description   TEXT,
    max_score     NUMERIC(5,2) NOT NULL DEFAULT 10,
    weight        NUMERIC(3,2) NOT NULL DEFAULT 1.00
);

-- 21. experiment_assignments - Giao bai thi nghiem cho lop hoc
CREATE TABLE IF NOT EXISTS experiment_assignments (
    assignment_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id        UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    experiment_id   UUID NOT NULL REFERENCES experiments(experiment_id) ON DELETE CASCADE,
    deadline        TIMESTAMPTZ,
    require_confirm BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 22. experiment_submissions - Bai nop bao cao thi nghiem cua sinh vien
CREATE TABLE IF NOT EXISTS experiment_submissions (
    submission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID NOT NULL REFERENCES experiment_assignments(assignment_id) ON DELETE CASCADE,
    student_id    UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    data_json     JSONB,
    status        VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    submitted_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (assignment_id, student_id)
);

-- 23. experiment_scores - Ket qua cham diem bai thi nghiem
CREATE TABLE IF NOT EXISTS experiment_scores (
    score_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL UNIQUE REFERENCES experiment_submissions(submission_id) ON DELETE CASCADE,
    graded_by     UUID REFERENCES users(user_id) ON DELETE SET NULL,
    final_score   NUMERIC(5,2) NOT NULL,
    feedback      TEXT,
    graded_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 24. experiment_confirmations - Xac nhan tham gia truc tiep phong thi nghiem
CREATE TABLE IF NOT EXISTS experiment_confirmations (
    confirmation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id   UUID NOT NULL UNIQUE REFERENCES experiment_submissions(submission_id) ON DELETE CASCADE,
    confirmed_by    UUID REFERENCES users(user_id) ON DELETE SET NULL,
    note            TEXT,
    confirmed_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 25. question_bank - Ngan hang cau hoi trac nghiem
CREATE TABLE IF NOT EXISTS question_bank (
    question_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id      UUID NOT NULL REFERENCES topics(topic_id) ON DELETE CASCADE,
    question_text TEXT NOT NULL,
    question_type VARCHAR(50) NOT NULL DEFAULT 'MCQ_SINGLE',
    difficulty    VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    explanation   TEXT,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 26. question_options - Cac phuong an lua chon cua cau hoi
CREATE TABLE IF NOT EXISTS question_options (
    option_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id   UUID NOT NULL REFERENCES question_bank(question_id) ON DELETE CASCADE,
    option_label  VARCHAR(10),
    option_text   TEXT NOT NULL,
    is_correct    BOOLEAN NOT NULL DEFAULT FALSE,
    order_index   INT NOT NULL DEFAULT 0
);

-- 27. exam_matrix - Ma tran cau truc de thi
CREATE TABLE IF NOT EXISTS exam_matrix (
    matrix_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id   UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE CASCADE,
    matrix_name  VARCHAR(255) NOT NULL,
    description  TEXT,
    total_points NUMERIC(5,2) NOT NULL DEFAULT 100,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 28. exam_matrix_details - Chi tiet ma tran theo chu de & do kho
CREATE TABLE IF NOT EXISTS exam_matrix_details (
    detail_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    matrix_id    UUID NOT NULL REFERENCES exam_matrix(matrix_id) ON DELETE CASCADE,
    topic_id     UUID NOT NULL REFERENCES topics(topic_id) ON DELETE CASCADE,
    difficulty   VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    question_qty INT NOT NULL DEFAULT 1,
    point_each   NUMERIC(4,2) NOT NULL DEFAULT 1.00
);

-- 29. exams - Quan ly de thi / bai kiem tra truc tuyen
CREATE TABLE IF NOT EXISTS exams (
    exam_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id         UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    matrix_id        UUID REFERENCES exam_matrix(matrix_id) ON DELETE SET NULL,
    title            VARCHAR(255) NOT NULL,
    exam_type        VARCHAR(50) NOT NULL DEFAULT 'PRACTICE',
    duration_minutes INT NOT NULL DEFAULT 45,
    max_attempts     INT NOT NULL DEFAULT 3,
    start_time       TIMESTAMPTZ,
    end_time         TIMESTAMPTZ,
    shuffle_questions BOOLEAN NOT NULL DEFAULT TRUE,
    shuffle_options  BOOLEAN NOT NULL DEFAULT TRUE,
    is_published     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 30. exam_questions - Cau hoi thuoc de thi
CREATE TABLE IF NOT EXISTS exam_questions (
    exam_id      UUID NOT NULL REFERENCES exams(exam_id) ON DELETE CASCADE,
    question_id  UUID NOT NULL REFERENCES question_bank(question_id) ON DELETE CASCADE,
    point_weight NUMERIC(4,2) NOT NULL DEFAULT 1.00,
    order_index  INT NOT NULL DEFAULT 0,
    PRIMARY KEY (exam_id, question_id)
);

-- 31. exam_attempts - Luot lam bai thi cua sinh vien
CREATE TABLE IF NOT EXISTS exam_attempts (
    attempt_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id         UUID NOT NULL REFERENCES exams(exam_id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    attempt_number  INT NOT NULL DEFAULT 1,
    status          VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at    TIMESTAMPTZ,
    total_score     NUMERIC(5,2),
    UNIQUE (exam_id, student_id, attempt_number)
);

-- 32. exam_answers - Cau tra loi tung cau cua sinh vien
CREATE TABLE IF NOT EXISTS exam_answers (
    answer_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id   UUID NOT NULL REFERENCES exam_attempts(attempt_id) ON DELETE CASCADE,
    question_id  UUID NOT NULL REFERENCES question_bank(question_id) ON DELETE CASCADE,
    answer_text  TEXT,
    is_correct   BOOLEAN,
    score_earned NUMERIC(5,2) NOT NULL DEFAULT 0,
    UNIQUE (attempt_id, question_id)
);

-- 33. learning_progress - Tien do hoc tap theo chu de
CREATE TABLE IF NOT EXISTS learning_progress (
    progress_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id         UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    class_id           UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    topic_id           UUID NOT NULL REFERENCES topics(topic_id) ON DELETE CASCADE,
    status             VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    progress_percent   NUMERIC(5,2) NOT NULL DEFAULT 0,
    last_accessed_at   TIMESTAMPTZ,
    UNIQUE (student_id, class_id, topic_id)
);

-- 34. activity_logs - Nhat ky hoat dong chi tiet
CREATE TABLE IF NOT EXISTS activity_logs (
    log_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    class_id      UUID REFERENCES classes(class_id) ON DELETE SET NULL,
    action_type   VARCHAR(100) NOT NULL,
    object_type   VARCHAR(100),
    object_id     UUID,
    metadata_json JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 35. evidence_repository - Kho ho so minh chung danh gia sinh vien
CREATE TABLE IF NOT EXISTS evidence_repository (
    evidence_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id   UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    source_type  VARCHAR(50) NOT NULL,
    source_id    UUID NOT NULL,
    file_id      UUID REFERENCES file_uploads(file_id) ON DELETE SET NULL,
    file_url     TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 36. dashboard_snapshots - Cache du lieu tong hop bang dieu khien
CREATE TABLE IF NOT EXISTS dashboard_snapshots (
    snapshot_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id      UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    student_id    UUID REFERENCES users(user_id) ON DELETE CASCADE,
    period        VARCHAR(50) NOT NULL,
    data_json     JSONB NOT NULL,
    generated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 37. topic_difficulty_stats - Thong ke noi dung kho theo chu de
CREATE TABLE IF NOT EXISTS topic_difficulty_stats (
    stat_id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id                  UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE CASCADE,
    topic_id                    UUID NOT NULL REFERENCES topics(topic_id) ON DELETE CASCADE,
    class_id                    UUID REFERENCES classes(class_id) ON DELETE CASCADE,
    avg_score                   NUMERIC(6,2),
    error_rate                  NUMERIC(5,2),
    common_wrong_options_json   JSONB,
    period                      VARCHAR(50) NOT NULL,
    generated_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 38. question_stats - Thong ke do kho va do phan biet cau hoi
CREATE TABLE IF NOT EXISTS question_stats (
    question_id            UUID PRIMARY KEY REFERENCES question_bank(question_id) ON DELETE CASCADE,
    times_used             INT NOT NULL DEFAULT 0,
    correct_rate           NUMERIC(5,2),
    discrimination_index   NUMERIC(5,2),
    avg_time_seconds       NUMERIC(8,2),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 39. ai_topic_gap_stats - Thong ke lo hong kien thuc AI ghi nhan
CREATE TABLE IF NOT EXISTS ai_topic_gap_stats (
    gap_id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id            UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE CASCADE,
    topic_id              UUID REFERENCES topics(topic_id) ON DELETE CASCADE,
    refusal_count         INT NOT NULL DEFAULT 0,
    frequent_query_sample TEXT,
    period                VARCHAR(50) NOT NULL,
    generated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 40. material_effectiveness_stats - Danh gia hieu qua thuc te hoc lieu
CREATE TABLE IF NOT EXISTS material_effectiveness_stats (
    material_id                     UUID NOT NULL REFERENCES learning_materials(material_id) ON DELETE CASCADE,
    period                          VARCHAR(50) NOT NULL,
    view_count                      INT NOT NULL DEFAULT 0,
    avg_time_spent_seconds          NUMERIC(8,2),
    correlated_score_improvement    NUMERIC(6,2),
    PRIMARY KEY (material_id, period)
);

-- 41. audit_logs - Nhat ky kiem toan thay doi du lieu
CREATE TABLE IF NOT EXISTS audit_logs (
    audit_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID REFERENCES users(user_id) ON DELETE SET NULL,
    action       VARCHAR(100) NOT NULL,
    entity       VARCHAR(100) NOT NULL,
    entity_id    UUID,
    old_value    JSONB,
    new_value    JSONB,
    ip_address   INET,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 42. system_settings - Cai dat cau hinh he thong
CREATE TABLE IF NOT EXISTS system_settings (
    key         VARCHAR(100) NOT NULL,
    subject_id  UUID REFERENCES subjects(subject_id) ON DELETE CASCADE,
    value       JSONB NOT NULL,
    PRIMARY KEY (key, subject_id)
);

-- 43. password_reset_tokens - Token khoi phuc mat khau qua email
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    token_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token        VARCHAR(100) NOT NULL UNIQUE,
    user_id      UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    email        VARCHAR(255) NOT NULL,
    expiry_date  TIMESTAMPTZ NOT NULL,
    used         BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- INDEXES
CREATE INDEX IF NOT EXISTS idx_users_username         ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email            ON users(email);
CREATE INDEX IF NOT EXISTS idx_classes_subject        ON classes(subject_id);
CREATE INDEX IF NOT EXISTS idx_classes_semester       ON classes(semester_id);
CREATE INDEX IF NOT EXISTS idx_enrollments_student    ON class_enrollments(student_id);
CREATE INDEX IF NOT EXISTS idx_topics_subject         ON topics(subject_id);
CREATE INDEX IF NOT EXISTS idx_materials_topic        ON learning_materials(topic_id);
CREATE INDEX IF NOT EXISTS idx_ai_conv_student        ON ai_conversations(student_id);
CREATE INDEX IF NOT EXISTS idx_ai_messages_conv       ON ai_messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_questions_topic        ON question_bank(topic_id);
CREATE INDEX IF NOT EXISTS idx_exams_class            ON exams(class_id);
CREATE INDEX IF NOT EXISTS idx_exam_attempts_student  ON exam_attempts(student_id);
CREATE INDEX IF NOT EXISTS idx_progress_student_class ON learning_progress(student_id, class_id);
CREATE INDEX IF NOT EXISTS idx_activity_logs_user     ON activity_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_pwd_reset_token        ON password_reset_tokens(token);


-- =============================================================================
-- PHAN 2: DU LIEU KHOI TAO MAU (SEED DATA)
-- =============================================================================

-- 1. NGUOI DUNG MAC DINH (Mat khau duoc ma hoa bang BCrypt rounds=12)
-- admin:     admin123456
-- gv_nguyen: gv123456
-- sv_an:     sv123456
-- sv_cuong:  sv123456
INSERT INTO users (user_id, username, email, password_hash, role, status)
VALUES 
    ('11111111-1111-1111-1111-111111111111', 'admin', 'admin@email.com', '$2a$12$KwPIzRh4sJOiDShjPzllfubzvE7LebIwq9M9aNf8mfMImmowOdP/y', 'ADMIN', 'ACTIVE'),
    ('22222222-2222-2222-2222-222222222222', 'gv_nguyen', 'gv_nguyen@email.com', '$2a$12$UOn67b.ByGn6gT2N9ezJTOXioKeuGHRG8UK27iymeJDIHO/t7y2Li', 'INSTRUCTOR', 'ACTIVE'),
    ('33333333-3333-3333-3333-333333333333', 'sv_an', 'sv_an@email.com', '$2a$12$RVuzzO/14UP7mn2KmOub3eCGpj3N5ikKqOifx7UPEa8sch2ScbbO.', 'STUDENT', 'ACTIVE'),
    ('44444444-4444-4444-4444-444444444444', 'sv_cuong', 'sv_cuong@email.com', '$2a$12$RVuzzO/14UP7mn2KmOub3eCGpj3N5ikKqOifx7UPEa8sch2ScbbO.', 'STUDENT', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;

-- 2. HO SO NGUOI DUNG (user_profiles)
INSERT INTO user_profiles (user_id, full_name, avatar_url, date_of_birth, gender, phone, student_code, bio)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Quản Trị Viên Hệ Thống', 'http://localhost:9000/vatly1-bucket/avatars/admin.png', '1990-01-01', 'MALE', '0901234567', 'ADMIN01', 'Quản trị viên hệ thống Vật lý 1'),
    ('22222222-2222-2222-2222-222222222222', 'ThS. Nguyễn Văn Giảng', 'http://localhost:9000/vatly1-bucket/avatars/gv_nguyen.png', '1985-05-15', 'MALE', '0912345678', 'GV001', 'Giảng viên Bộ môn Vật lý đại cương'),
    ('33333333-3333-3333-3333-333333333333', 'Lê Văn An', 'http://localhost:9000/vatly1-bucket/avatars/sv_an.png', '2003-08-20', 'MALE', '0987654321', 'B21DCCN001', 'Sinh viên Khoa CNTT'),
    ('44444444-4444-4444-4444-444444444444', 'Trần Quốc Cường', 'http://localhost:9000/vatly1-bucket/avatars/sv_cuong.png', '2003-11-10', 'MALE', '0976543210', 'B21DCCN002', 'Sinh viên Khoa Điện tử')
ON CONFLICT (user_id) DO NOTHING;

-- 3. MON HOC (subjects)
INSERT INTO subjects (subject_id, subject_code, subject_name, description, is_active)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'PHY101', 'Vật lý đại cương 1', 'Học phần Vật lý 1 trang bị các kiến thức nền tảng về Cơ học, Nhiệt học và kỹ năng thực hành thí nghiệm ảo.', true)
ON CONFLICT (subject_code) DO NOTHING;

-- 4. HOC KY (semesters)
INSERT INTO semesters (semester_id, semester_code, semester_name, academic_year, start_date, end_date, is_current)
VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '20251', 'Học kỳ 1', '2025-2026', '2025-09-01', '2026-01-15', true)
ON CONFLICT (semester_code) DO NOTHING;

-- 5. LOP HOC PHAN (classes)
INSERT INTO classes (class_id, subject_id, semester_id, class_code, instructor_id, max_students, status)
VALUES
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'PHY101-01', '22222222-2222-2222-2222-222222222222', 60, 'ACTIVE')
ON CONFLICT (subject_id, semester_id, class_code) DO NOTHING;

-- 6. PHAN CONG GIANG VIEN (class_staff)
INSERT INTO class_staff (class_id, user_id, role)
VALUES
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', '22222222-2222-2222-2222-222222222222', 'INSTRUCTOR')
ON CONFLICT (class_id, user_id) DO NOTHING;

-- 7. GHI DANH SINH VIEN (class_enrollments)
INSERT INTO class_enrollments (class_id, student_id, status)
VALUES
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', '33333333-3333-3333-3333-333333333333', 'ACTIVE'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', '44444444-4444-4444-4444-444444444444', 'ACTIVE')
ON CONFLICT (class_id, student_id) DO NOTHING;

-- 8. DANH SACH 7 CHU DE BAI HOC (topics)
INSERT INTO topics (topic_id, subject_id, topic_code, topic_name, description, order_index)
VALUES
    ('d1111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'CH01', 'Động học chất điểm', 'Chuyển động thẳng đều, biến đổi đều, chuyển động tròn và chuyển động cong.', 1),
    ('d2222222-2222-2222-2222-222222222222', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'CH02', 'Động lực học chất điểm', 'Ba định luật Newton, các lực cơ học cơ bản, phương trình vi phân chuyển động.', 2),
    ('d3333333-3333-3333-3333-333333333333', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'CH03', 'Công và Năng lượng', 'Định lý động năng, thế năng trong trường lực thế, định luật bảo toàn cơ năng.', 3),
    ('d4444444-4444-4444-4444-444444444444', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'CH04', 'Cơ học vật rắn', 'Mômen quán tính, phương trình cơ bản của chuyển động quay của vật rắn quanh trục cố định.', 4),
    ('d5555555-5555-5555-5555-555555555555', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'CH05', 'Dao động cơ và Sóng cơ', 'Dao động điều hòa, con lắc lò xo, con lắc đơn, sự lan truyền sóng cơ và giao thoa sóng.', 5),
    ('d6666666-6666-6666-6666-666666666666', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'CH06', 'Thuyết động học phân tử chất khí', 'Các định luật thực nghiệm về chất khí lý tưởng, phương trình Clapeyron - Mendeleev.', 6),
    ('d7777777-7777-7777-7777-777777777777', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'CH07', 'Nhiệt động lực học', 'Nguyên lý thứ nhất và nguyên lý thứ hai của nhiệt động lực học, chu trình Carnot.', 7)
ON CONFLICT (subject_id, topic_code) DO NOTHING;

-- 9. NGAN HANG CAU HOI TRAC NGHIEM MAU (question_bank & question_options)
-- Cau 1 (Chu de 1 - De)
INSERT INTO question_bank (question_id, topic_id, question_text, question_type, difficulty, explanation, is_active)
VALUES
    ('e1111111-1111-1111-1111-111111111111', 'd1111111-1111-1111-1111-111111111111', 'Trong chuyển động thẳng biến đổi đều, gia tốc là một đại lượng:', 'MCQ_SINGLE', 'EASY', 'Trong chuyển động thẳng biến đổi đều, vận tốc biến đổi đều theo thời gian nên gia tốc là hằng số.', true)
ON CONFLICT (question_id) DO NOTHING;

INSERT INTO question_options (option_id, question_id, option_label, option_text, is_correct, order_index)
VALUES
    (gen_random_uuid(), 'e1111111-1111-1111-1111-111111111111', 'A', 'Biến thiên liên tục theo hàm bậc nhất của thời gian', false, 1),
    (gen_random_uuid(), 'e1111111-1111-1111-1111-111111111111', 'B', 'Không đổi cả về phương, chiều và độ lớn theo thời gian', true, 2),
    (gen_random_uuid(), 'e1111111-1111-1111-1111-111111111111', 'C', 'Luôn luôn bằng không', false, 3),
    (gen_random_uuid(), 'e1111111-1111-1111-1111-111111111111', 'D', 'Tăng dần đều theo thời gian', false, 4);

-- Cau 2 (Chu de 2 - Trung binh)
INSERT INTO question_bank (question_id, topic_id, question_text, question_type, difficulty, explanation, is_active)
VALUES
    ('e2222222-2222-2222-2222-222222222222', 'd2222222-2222-2222-2222-222222222222', 'Theo định luật II Newton, gia tốc mà một vật thu được có đặc điểm nào sau đây?', 'MCQ_SINGLE', 'MEDIUM', 'Định luật II Newton: F = m*a => a = F/m, gia tốc tỉ lệ thuận với hợp lực và tỉ lệ nghịch với khối lượng.', true)
ON CONFLICT (question_id) DO NOTHING;

INSERT INTO question_options (option_id, question_id, option_label, option_text, is_correct, order_index)
VALUES
    (gen_random_uuid(), 'e2222222-2222-2222-2222-222222222222', 'A', 'Cùng hướng và tỉ lệ thuận với hợp lực tác dụng lên vật', true, 1),
    (gen_random_uuid(), 'e2222222-2222-2222-2222-222222222222', 'B', 'Ngược hướng với hợp lực tác dụng lên vật', false, 2),
    (gen_random_uuid(), 'e2222222-2222-2222-2222-222222222222', 'C', 'Tỉ lệ thuận với khối lượng của vật', false, 3),
    (gen_random_uuid(), 'e2222222-2222-2222-2222-222222222222', 'D', 'Không phụ thuộc vào khối lượng của vật', false, 4);

-- Cau 3 (Chu de 3 - Kho)
INSERT INTO question_bank (question_id, topic_id, question_text, question_type, difficulty, explanation, is_active)
VALUES
    ('e3333333-3333-3333-3333-333333333333', 'd3333333-3333-3333-3333-333333333333', 'Một vật khối lượng m trượt không vận tốc đầu từ đỉnh một mặt phẳng nghiêng có độ cao h. Bỏ qua mọi ma sát. Vận tốc của vật khi đến chân dốc là:', 'MCQ_SINGLE', 'HARD', 'Theo định luật bảo toàn cơ năng: m*g*h = (1/2)*m*v^2 => v = sqrt(2*g*h).', true)
ON CONFLICT (question_id) DO NOTHING;

INSERT INTO question_options (option_id, question_id, option_label, option_text, is_correct, order_index)
VALUES
    (gen_random_uuid(), 'e3333333-3333-3333-3333-333333333333', 'A', 'v = g * h', false, 1),
    (gen_random_uuid(), 'e3333333-3333-3333-3333-333333333333', 'B', 'v = sqrt(g * h)', false, 2),
    (gen_random_uuid(), 'e3333333-3333-3333-3333-333333333333', 'C', 'v = sqrt(2 * g * h)', true, 3),
    (gen_random_uuid(), 'e3333333-3333-3333-3333-333333333333', 'D', 'v = 2 * g * h', false, 4);

-- 10. BAI THI NGHIEM AO MAU (experiments & experiment_assignments)
INSERT INTO experiments (experiment_id, subject_id, title, description, instruction, is_active)
VALUES
    ('f1111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Bài thí nghiệm 1: Xác định gia tốc trọng trường g bằng con lắc đơn', 'Thí nghiệm đo chu kỳ dao động T của con lắc đơn với các chiều dài l khác nhau để xác định gia tốc rơi tự do g.', '1. Lắp đặt con lắc đơn với chiều dài dây l = 0.8m, 0.9m, 1.0m.
2. Kéo lệch góc nhỏ (<10 độ) và bấm giờ 10 chu kỳ dao động.
3. Tính chu kỳ trung bình T và tính g = 4*pi^2*l / T^2.
4. Điền số liệu đo và đính kèm ảnh minh chứng bài làm.', true)
ON CONFLICT (experiment_id) DO NOTHING;

INSERT INTO experiment_assignments (assignment_id, class_id, experiment_id, deadline, require_confirm)
VALUES
    ('f2222222-2222-2222-2222-222222222222', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'f1111111-1111-1111-1111-111111111111', CURRENT_TIMESTAMP + INTERVAL '30 days', false)
ON CONFLICT (assignment_id) DO NOTHING;

-- 11. CAI DAT HE THONG MAC DINH (system_settings)
INSERT INTO system_settings (key, subject_id, value)
VALUES
    ('exam.max_attempts', NULL, '{"value": 3}'),
    ('exam.pass_percentage', NULL, '{"value": 50}'),
    ('ai.tutor_enabled', NULL, '{"value": true}'),
    ('file.max_size_mb', NULL, '{"value": 50}')
ON CONFLICT (key, subject_id) DO NOTHING;

-- =============================================================================
-- HOAN TAT KHOI TAO CO SO DU LIEU
-- =============================================================================
