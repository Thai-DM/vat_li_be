-- ============================================================================
-- PHASE 6 STEP 1: ADDITIVE MIGRATION FOR FILL_BLANK ACCEPTED ANSWERS
-- ============================================================================
-- Safe additive migration:
-- 1. Does not modify or drop question_bank or question_options
-- 2. Does not touch existing MCQ data or exam answers
-- 3. Creates question_accepted_answers table and index
-- ============================================================================

CREATE TABLE IF NOT EXISTS question_accepted_answers (
    accepted_answer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id        UUID NOT NULL REFERENCES question_bank(question_id) ON DELETE CASCADE,
    accepted_text      VARCHAR(500) NOT NULL,
    order_index        INT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_question_accepted_answers_qid 
    ON question_accepted_answers(question_id);
