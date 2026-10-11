-- =============================================================================
-- MIGRATION PHASE 3 — STEP 2: CHAPTER & TOPIC HIERARCHY MIGRATION
-- Database: PostgreSQL 14+ (Tested on Neon PostgreSQL 18.6)
-- Target: Subject-Centric Chapter Entity, Chapter-Topic 1-N Hierarchy,
--         Composite Foreign Key (chapter_id, subject_id) for Cross-Subject Safety
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
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'question_bank') THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Table "question_bank" does not exist.';
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 2. CREATE TABLE chapters (Subject-Centric Chapter Structure)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chapters (
    chapter_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id   UUID NOT NULL,
    chapter_code VARCHAR(50) NOT NULL,
    chapter_name VARCHAR(255) NOT NULL,
    description  TEXT,
    order_index  INTEGER NOT NULL DEFAULT 0,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- FK subject_id -> subjects(subject_id) (ON DELETE RESTRICT - no cascade delete on curriculum)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_chapters_subject' AND table_name = 'chapters'
    ) THEN
        ALTER TABLE chapters
            ADD CONSTRAINT fk_chapters_subject
            FOREIGN KEY (subject_id) REFERENCES subjects(subject_id) ON DELETE RESTRICT;
    END IF;
END $$;

-- UNIQUE(subject_id, chapter_code) - Mỗi môn có mã chương duy nhất (vd CH01, CH02)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'uq_chapters_subject_code' AND table_name = 'chapters'
    ) THEN
        ALTER TABLE chapters
            ADD CONSTRAINT uq_chapters_subject_code
            UNIQUE (subject_id, chapter_code);
    END IF;
END $$;

-- UNIQUE(chapter_id, subject_id) - Hỗ trợ Composite Foreign Key từ topics(chapter_id, subject_id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'uq_chapters_id_subject' AND table_name = 'chapters'
    ) THEN
        ALTER TABLE chapters
            ADD CONSTRAINT uq_chapters_id_subject
            UNIQUE (chapter_id, subject_id);
    END IF;
END $$;

-- Indexes for chapters
CREATE INDEX IF NOT EXISTS idx_chapters_subject_order ON chapters(subject_id, order_index);
CREATE INDEX IF NOT EXISTS idx_chapters_subject_active ON chapters(subject_id, is_active);

-- -----------------------------------------------------------------------------
-- 3. EXPAND TABLE topics: Add chapter_id NULL and Composite Foreign Key
-- -----------------------------------------------------------------------------
ALTER TABLE topics ADD COLUMN IF NOT EXISTS chapter_id UUID NULL;

-- Composite FK (chapter_id, subject_id) -> chapters(chapter_id, subject_id) ON DELETE RESTRICT
-- Ensures:
-- 1) Topic cannot link to a Chapter belonging to another Subject.
-- 2) Chapter cannot be deleted while it contains topics (ON DELETE RESTRICT).
-- 3) Topic with chapter_id NULL remains completely valid (backward-compatible).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_topics_chapter_subject' AND table_name = 'topics'
    ) THEN
        ALTER TABLE topics
            ADD CONSTRAINT fk_topics_chapter_subject
            FOREIGN KEY (chapter_id, subject_id)
            REFERENCES chapters(chapter_id, subject_id)
            ON DELETE RESTRICT;
    END IF;
END $$;

-- Indexes on topics for chapter queries
CREATE INDEX IF NOT EXISTS idx_topics_chapter ON topics(chapter_id);
CREATE INDEX IF NOT EXISTS idx_topics_chapter_subject ON topics(chapter_id, subject_id);

-- -----------------------------------------------------------------------------
-- 4. POST-MIGRATION INTEGRITY SANITY CHECK
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    v_topic_count INT;
    v_qb_count INT;
BEGIN
    -- Verify existing topics remain unaltered and have topic_name intact
    SELECT COUNT(*) INTO v_topic_count FROM topics;
    IF v_topic_count < 1 THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: Expected at least 1 existing topic, found %', v_topic_count;
    END IF;

    -- Verify baseline topic 'qưer' remains intact with chapter_id NULL
    IF NOT EXISTS (
        SELECT 1 FROM topics
        WHERE topic_id = '118332e1-36a0-4d70-b47f-fefeaae722a6'::uuid
          AND chapter_id IS NULL
    ) THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: Baseline topic 118332e1-36a0-4d70-b47f-fefeaae722a6 was altered or missing.';
    END IF;

    -- Verify question_bank count remains at least 8
    SELECT COUNT(*) INTO v_qb_count FROM question_bank;
    IF v_qb_count < 8 THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: Expected at least 8 questions, found %', v_qb_count;
    END IF;

    -- Verify all required constraints exist in information_schema
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_chapters_subject') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: fk_chapters_subject missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'uq_chapters_subject_code') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: uq_chapters_subject_code missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'uq_chapters_id_subject') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: uq_chapters_id_subject missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_topics_chapter_subject') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: fk_topics_chapter_subject missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'topics' AND column_name = 'chapter_id') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: column topics.chapter_id missing';
    END IF;
END $$;

COMMIT;
