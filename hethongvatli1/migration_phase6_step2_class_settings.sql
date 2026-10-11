-- Migration Phase 6 Step 2: Class Settings Table
CREATE TABLE IF NOT EXISTS class_settings (
    class_id UUID PRIMARY KEY REFERENCES classes(class_id) ON DELETE CASCADE,
    practice_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    quiz_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    max_practice_attempts INT NOT NULL DEFAULT 10,
    practice_show_solution BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES users(user_id) ON DELETE SET NULL
);

-- Seed default settings for all existing classes if not already present
INSERT INTO class_settings (class_id, practice_enabled, quiz_enabled, max_practice_attempts, practice_show_solution)
SELECT class_id, TRUE, TRUE, 10, TRUE FROM classes
ON CONFLICT (class_id) DO NOTHING;
