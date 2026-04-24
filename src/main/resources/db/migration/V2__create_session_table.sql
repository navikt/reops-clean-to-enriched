CREATE TABLE session (
    session_id UUID        PRIMARY KEY,
    website_id UUID        NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
