-- Migration Phase 6 Step 3: Adaptive Practice Recommendation Tables
CREATE TABLE IF NOT EXISTS practice_sessions (
    session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    class_id UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subjects(subject_id) ON DELETE CASCADE,
    total_questions INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    total_score NUMERIC(5, 2) DEFAULT 0.00,
    accuracy_rate NUMERIC(5, 4) DEFAULT 0.0000,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS practice_session_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES practice_sessions(session_id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES question_bank(question_id) ON DELETE CASCADE,
    topic_id UUID REFERENCES topics(topic_id) ON DELETE SET NULL,
    order_index INTEGER NOT NULL,
    is_reviewed BOOLEAN DEFAULT FALSE,
    CONSTRAINT uq_session_question UNIQUE (session_id, question_id)
);

CREATE TABLE IF NOT EXISTS practice_session_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES practice_sessions(session_id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES question_bank(question_id) ON DELETE CASCADE,
    selected_option_ids VARCHAR(500),
    answer_text TEXT,
    is_correct BOOLEAN,
    score NUMERIC(5, 2),
    answered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_session_answer UNIQUE (session_id, question_id),
    CONSTRAINT fk_answer_session_question FOREIGN KEY (session_id, question_id) REFERENCES practice_session_questions(session_id, question_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_practice_sessions_student_class ON practice_sessions(student_id, class_id);
CREATE INDEX IF NOT EXISTS idx_practice_sessions_status ON practice_sessions(status);
CREATE INDEX IF NOT EXISTS idx_practice_session_questions_session ON practice_session_questions(session_id);
CREATE INDEX IF NOT EXISTS idx_practice_session_answers_session ON practice_session_answers(session_id);

-- Idempotent check: Ensure fk_answer_session_question exists even if table was pre-existing
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'practice_session_answers')
       AND NOT EXISTS (
           SELECT 1 FROM pg_constraint
           WHERE conname = 'fk_answer_session_question' AND conrelid = 'practice_session_answers'::regclass
       ) THEN
        ALTER TABLE practice_session_answers
            ADD CONSTRAINT fk_answer_session_question
            FOREIGN KEY (session_id, question_id)
            REFERENCES practice_session_questions(session_id, question_id)
            ON DELETE CASCADE;
    END IF;
END $$;
