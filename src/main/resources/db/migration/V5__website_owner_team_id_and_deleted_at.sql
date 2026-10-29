-- Additive website management columns.
-- owner_team_id: Team Catalog (teamkatalog.nav.no) UUID of the team that owns the
-- registration. NULL means unassigned — existing rows intentionally stay unassigned;
-- ownership is never inferred from name/domain.
-- deleted_at: soft-delete marker. NULL = active. Soft-deleted rows keep their data
-- but are excluded from ingestion (isAllowed) and get a DELETE tombstone on Kafka.
ALTER TABLE website ADD COLUMN owner_team_id UUID;
ALTER TABLE website ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_website_owner_team_id ON website(owner_team_id);
