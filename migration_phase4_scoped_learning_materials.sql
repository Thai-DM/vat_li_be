-- =============================================================================
-- MIGRATION PHASE 4 — STEP 3: SCOPED LEARNING MATERIALS (SHARED & CLASS)
-- Database: PostgreSQL 14+ (Tested on Neon PostgreSQL 18.6)
-- Target: Single-table learning_materials expansion with scope, subject_id, class_id
--         Composite Foreign Keys (topic_id, subject_id) -> topics(topic_id, subject_id)
--         Composite Foreign Keys (class_id, subject_id) -> classes(class_id, subject_id)
--         CHECK constraints for Scope & Scope-Class consistency
--         ON DELETE RESTRICT policy for learning material preservation
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
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'classes') THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Table "classes" does not exist.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'learning_materials') THEN
        RAISE EXCEPTION 'PREFLIGHT FAILED: Table "learning_materials" does not exist.';
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 2. ENSURE COMPOSITE UNIQUE CONSTRAINTS ON REFERENCED TABLES
-- -----------------------------------------------------------------------------

-- A. topics(topic_id, subject_id) - Target for fk_materials_topic_subject
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

-- B. classes(class_id, subject_id) - Target for fk_materials_class_subject
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'uq_classes_id_subject' AND table_name = 'classes'
    ) THEN
        ALTER TABLE classes
            ADD CONSTRAINT uq_classes_id_subject
            UNIQUE (class_id, subject_id);
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 3. EXPAND TABLE learning_materials: COLUMNS & BACKFILL
-- -----------------------------------------------------------------------------

-- A. Add subject_id nullable temporarily
ALTER TABLE learning_materials ADD COLUMN IF NOT EXISTS subject_id UUID NULL;

-- B. Backfill subject_id from topics for all existing/legacy learning materials
UPDATE learning_materials lm
SET subject_id = t.subject_id
FROM topics t
WHERE lm.topic_id = t.topic_id AND lm.subject_id IS NULL;

-- C. Verify no dangling materials without subject_id
DO $$
DECLARE
    v_orphan_count INT;
BEGIN
    SELECT COUNT(*) INTO v_orphan_count FROM learning_materials WHERE subject_id IS NULL;
    IF v_orphan_count > 0 THEN
        RAISE EXCEPTION 'BACKFILL FAILED: Found % learning_materials with NULL subject_id (no matching topic).', v_orphan_count;
    END IF;
END $$;

-- D. Enforce NOT NULL on subject_id
ALTER TABLE learning_materials ALTER COLUMN subject_id SET NOT NULL;

-- E. Add scope column with default 'SHARED'
ALTER TABLE learning_materials ADD COLUMN IF NOT EXISTS scope VARCHAR(20) NOT NULL DEFAULT 'SHARED';

-- F. Safe legacy backfill: only set 'SHARED' if scope is NULL (do not overwrite future CLASS materials on re-run)
UPDATE learning_materials SET scope = 'SHARED' WHERE scope IS NULL;

-- G. Add class_id column nullable
ALTER TABLE learning_materials ADD COLUMN IF NOT EXISTS class_id UUID NULL;

-- -----------------------------------------------------------------------------
-- 4. CONSTRAINTS ON learning_materials
-- -----------------------------------------------------------------------------

-- A. Scope check constraint ('SHARED' or 'CLASS')
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'chk_learning_materials_scope' AND table_name = 'learning_materials'
    ) THEN
        ALTER TABLE learning_materials
            ADD CONSTRAINT chk_learning_materials_scope
            CHECK (scope IN ('SHARED', 'CLASS'));
    END IF;
END $$;

-- B. Scope-Class consistency check constraint
-- SHARED: scope = 'SHARED' AND class_id IS NULL
-- CLASS:  scope = 'CLASS'  AND class_id IS NOT NULL
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'chk_learning_materials_scope_class' AND table_name = 'learning_materials'
    ) THEN
        ALTER TABLE learning_materials
            ADD CONSTRAINT chk_learning_materials_scope_class
            CHECK (
                (scope = 'SHARED' AND class_id IS NULL)
                OR
                (scope = 'CLASS' AND class_id IS NOT NULL)
            );
    END IF;
END $$;

-- C. Replace old legacy CASCADE FK on topic_id with authoritative RESTRICT composite FK
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'learning_materials_topic_id_fkey' AND table_name = 'learning_materials'
    ) THEN
        ALTER TABLE learning_materials DROP CONSTRAINT learning_materials_topic_id_fkey;
    END IF;
END $$;

-- D. Composite Foreign Key: (topic_id, subject_id) -> topics(topic_id, subject_id) ON DELETE RESTRICT
-- Ensures material and its topic always belong to the exact same subject.
-- Protects materials from accidental topic cascading deletes.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_materials_topic_subject' AND table_name = 'learning_materials'
    ) THEN
        ALTER TABLE learning_materials
            ADD CONSTRAINT fk_materials_topic_subject
            FOREIGN KEY (topic_id, subject_id)
            REFERENCES topics(topic_id, subject_id)
            ON DELETE RESTRICT;
    END IF;
END $$;

-- E. Composite Foreign Key: (class_id, subject_id) -> classes(class_id, subject_id) ON DELETE RESTRICT
-- When class_id IS NULL (SHARED scope), FK is satisfied by PostgreSQL MATCH SIMPLE semantics.
-- When class_id IS NOT NULL (CLASS scope), PostgreSQL enforces class_id and subject_id match classes.
-- Because learning_materials has only one subject_id, class and topic MUST belong to the exact same subject.
-- Protects materials from accidental class cascading deletes.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_materials_class_subject' AND table_name = 'learning_materials'
    ) THEN
        ALTER TABLE learning_materials
            ADD CONSTRAINT fk_materials_class_subject
            FOREIGN KEY (class_id, subject_id)
            REFERENCES classes(class_id, subject_id)
            ON DELETE RESTRICT;
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 5. PERFORMANCE INDEXES
-- -----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_materials_subject ON learning_materials(subject_id);
CREATE INDEX IF NOT EXISTS idx_materials_topic ON learning_materials(topic_id);
CREATE INDEX IF NOT EXISTS idx_materials_topic_subject ON learning_materials(topic_id, subject_id);
CREATE INDEX IF NOT EXISTS idx_materials_scope ON learning_materials(scope);
CREATE INDEX IF NOT EXISTS idx_materials_class ON learning_materials(class_id);
CREATE INDEX IF NOT EXISTS idx_materials_class_topic ON learning_materials(class_id, topic_id);
CREATE INDEX IF NOT EXISTS idx_materials_subject_scope ON learning_materials(subject_id, scope);
CREATE INDEX IF NOT EXISTS idx_materials_approval ON learning_materials(approval_status);
CREATE INDEX IF NOT EXISTS idx_materials_file_id ON learning_materials(file_id);

-- -----------------------------------------------------------------------------
-- 6. POST-MIGRATION INTEGRITY SANITY CHECK
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    -- Verify columns exist
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'learning_materials' AND column_name = 'subject_id' AND is_nullable = 'NO') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: column learning_materials.subject_id NOT NULL missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'learning_materials' AND column_name = 'scope' AND is_nullable = 'NO') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: column learning_materials.scope NOT NULL missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'learning_materials' AND column_name = 'class_id' AND is_nullable = 'YES') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: column learning_materials.class_id NULL missing';
    END IF;

    -- Verify unique constraints on referenced tables
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'uq_topics_id_subject') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: uq_topics_id_subject missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'uq_classes_id_subject') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: uq_classes_id_subject missing';
    END IF;

    -- Verify constraints on learning_materials
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'chk_learning_materials_scope') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: chk_learning_materials_scope missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'chk_learning_materials_scope_class') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: chk_learning_materials_scope_class missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_materials_topic_subject') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: fk_materials_topic_subject missing';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'fk_materials_class_subject') THEN
        RAISE EXCEPTION 'POST-MIGRATION CHECK FAILED: fk_materials_class_subject missing';
    END IF;
END $$;

COMMIT;
