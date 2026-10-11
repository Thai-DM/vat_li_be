-- ============================================================================
-- MIGRATION: CHUNKED FILE UPLOAD & MERGE SESSIONS
-- Database: PostgreSQL 14+ (Tested on Neon PostgreSQL)
-- Target: Table chunk_upload_sessions and supporting indexes
-- ============================================================================

CREATE TABLE IF NOT EXISTS chunk_upload_sessions (
    upload_id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    original_filename VARCHAR(500) NOT NULL,
    total_chunks INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'UPLOADING',
    file_size_bytes BIGINT,
    final_file_id UUID REFERENCES file_uploads(file_id) ON DELETE SET NULL,
    final_file_url VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_chunk_upload_sessions_owner ON chunk_upload_sessions(owner_id);
CREATE INDEX IF NOT EXISTS idx_chunk_upload_sessions_status ON chunk_upload_sessions(status);
CREATE INDEX IF NOT EXISTS idx_chunk_upload_sessions_expires ON chunk_upload_sessions(expires_at);
