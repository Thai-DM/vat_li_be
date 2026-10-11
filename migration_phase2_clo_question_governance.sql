-- =============================================================================
-- MIGRATION PHASE 2 — STEP 3: CLO & QUESTION GOVERNANCE AUDIT SCHEMA
-- Database: PostgreSQL 14+ (Tested on Neon PostgreSQL 18.6)
-- Target: Subject-Centric CLO, Question-CLO Mapping, Question Approvals Audit Trail
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
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'question_bank') THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Table "question_bank" does not exist.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'users') THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Table "users" does not exist.';
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 2. CREATE TABLE clos (Course Learning Outcomes)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS clos (
    clo_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id  UUID NOT NULL,
    clo_code    VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    order_index INT NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- FK subject_id -> subjects(subject_id) (ON DELETE RESTRICT - no cascade delete on curriculum)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_clos_subject' AND table_name = 'clos'
    ) THEN
        ALTER TABLE clos
            ADD CONSTRAINT fk_clos_subject
            FOREIGN KEY (subject_id) REFERENCES subjects(subject_id) ON DELETE RESTRICT;
    END IF;
END $$;

-- UNIQUE(subject_id, clo_code) - Mỗi môn có mã CLO duy nhất (vd CLO1, CLO2)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'uq_clos_subject_code' AND table_name = 'clos'
    ) THEN
        ALTER TABLE clos
            ADD CONSTRAINT uq_clos_subject_code
            UNIQUE (subject_id, clo_code);
    END IF;
END $$;

-- UNIQUE(clo_id, subject_id) - Hỗ trợ Composite Foreign Key từ question_clos
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

CREATE INDEX IF NOT EXISTS idx_clos_subject_order ON clos(subject_id, order_index);

-- -----------------------------------------------------------------------------
-- 3. ENHANCE question_bank: Add Composite Unique & Additive Audit Fields
-- -----------------------------------------------------------------------------

-- UNIQUE(question_id, subject_id) on question_bank to support Composite FK
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'uq_question_bank_id_subject' AND table_name = 'question_bank'
    ) THEN
        ALTER TABLE question_bank
            ADD CONSTRAINT uq_question_bank_id_subject
            UNIQUE (question_id, subject_id);
    END IF;
END $$;

-- Additive audit fields
ALTER TABLE question_bank ADD COLUMN IF NOT EXISTS reviewed_by UUID NULL;
ALTER TABLE question_bank ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ NULL;
ALTER TABLE question_bank ADD COLUMN IF NOT EXISTS rejection_reason TEXT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_question_bank_reviewed_by' AND table_name = 'question_bank'
    ) THEN
        ALTER TABLE question_bank
            ADD CONSTRAINT fk_question_bank_reviewed_by
            FOREIGN KEY (reviewed_by) REFERENCES users(user_id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_question_bank_reviewed_by ON question_bank(reviewed_by);

-- -----------------------------------------------------------------------------
-- 4. CREATE TABLE question_clos (Composite Foreign Keys for Cross-Subject Safety)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS question_clos (
    question_id UUID NOT NULL,
    clo_id      UUID NOT NULL,
    subject_id  UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_question_clos PRIMARY KEY (question_id, clo_id)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_question_clos_question_subject' AND table_name = 'question_clos'
    ) THEN
        ALTER TABLE question_clos
            ADD CONSTRAINT fk_question_clos_question_subject
            FOREIGN KEY (question_id, subject_id)
            REFERENCES question_bank(question_id, subject_id)
            ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_question_clos_clo_subject' AND table_name = 'question_clos'
    ) THEN
        ALTER TABLE question_clos
            ADD CONSTRAINT fk_question_clos_clo_subject
            FOREIGN KEY (clo_id, subject_id)
            REFERENCES clos(clo_id, subject_id)
            ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_question_clos_clo ON question_clos(clo_id);
CREATE INDEX IF NOT EXISTS idx_question_clos_subject ON question_clos(subject_id);

-- -----------------------------------------------------------------------------
-- 5. CREATE TABLE question_approvals (Append-Only Audit Trail)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS question_approvals (
    approval_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL,
    actor_id    UUID NOT NULL,
    action      VARCHAR(50) NOT NULL,
    from_status VARCHAR(50),
    to_status   VARCHAR(50) NOT NULL,
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_question_approvals_question' AND table_name = 'question_approvals'
    ) THEN
        ALTER TABLE question_approvals
            ADD CONSTRAINT fk_question_approvals_question
            FOREIGN KEY (question_id)
            REFERENCES question_bank(question_id)
            ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_question_approvals_actor' AND table_name = 'question_approvals'
    ) THEN
        ALTER TABLE question_approvals
            ADD CONSTRAINT fk_question_approvals_actor
            FOREIGN KEY (actor_id)
            REFERENCES users(user_id)
            ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_question_approvals_question_created
    ON question_approvals(question_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_question_approvals_actor
    ON question_approvals(actor_id);

-- -----------------------------------------------------------------------------
-- 6. POST-MIGRATION INTEGRITY SANITY CHECK
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    v_pending_count INT;
BEGIN
    -- Verify existing questions remain unaltered and in PENDING status
    SELECT COUNT(*) INTO v_pending_count
    FROM question_bank
    WHERE approval_status = 'PENDING' OR approval_status::text = 'pending';

    IF v_pending_count < 8 THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: Expected at least 8 pending questions, found %', v_pending_count;
    END IF;

    -- Verify constraints exist
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'uq_question_bank_id_subject') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: uq_question_bank_id_subject missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_question_clos_question_subject') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: fk_question_clos_question_subject missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_question_clos_clo_subject') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: fk_question_clos_clo_subject missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_question_approvals_question') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: fk_question_approvals_question missing';
    END IF;
END $$;

COMMIT;
