-- Chat sessions and messages: persistence for the RAG conversation history.
--
-- citations stored as JSONB: [{documentId, documentName, chunkIndex, excerpt, score}]
-- Denormalized at storage time so citations survive chunk deletion/rebuild.

CREATE TABLE chat_sessions (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_sessions_user_id ON chat_sessions(user_id);

CREATE TABLE chat_messages (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id       UUID        NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role             VARCHAR(20)  NOT NULL,  -- 'user' | 'assistant'
    content          TEXT         NOT NULL,
    citations        JSONB,
    prompt_tokens    INT,
    completion_tokens INT,
    model_used       VARCHAR(100),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_messages_session_id ON chat_messages(session_id);
