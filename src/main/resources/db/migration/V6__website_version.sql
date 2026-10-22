-- Optimistic-locking column on website to prevent lost-update races between an
-- authorized snapshot read in the controller and the service-layer re-read/save
-- (e.g. a concurrent admin ownership transfer must not be undone by a stale non-admin
-- edit, and soft-deleted rows must not be revived).
ALTER TABLE website ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
