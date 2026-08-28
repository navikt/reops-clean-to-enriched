ALTER TABLE website ADD COLUMN created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

-- Backfill: best available proxy for pre-existing rows is updated_at
UPDATE website SET created_at = updated_at;
