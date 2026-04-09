CREATE TABLE website (
    website_id UUID                     PRIMARY KEY,
    name       VARCHAR(255)             NOT NULL,
    domain     VARCHAR(255)             NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
