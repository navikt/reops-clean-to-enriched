# Website management API (backend)

This document describes the secure website-management API added to
`reops-clean-to-enriched`, its configuration, and rollout notes. It implements the
shared `website-management-contract.md`. The counterpart browser-facing BFF lives in
`reops-innblikk-frontend` (path prefix `/api/website-management`); this service exposes
the internal API directly.

## What changed

- The static single-page UI (`static/index.html`, `static/style.css`) and the legacy
  unauthenticated `GET/POST/PUT/DELETE /api/websites` controller are **removed**. They
  were the only consumers of those routes (the page's `fetch` calls), so removing both
  eliminates the unauthenticated bypass.
- The previous read-only `GET /api/manage/websites` is **replaced** by the full
  management API described below.
- Management requires a validated Azure AD OBO user token issued to the Innblikk BFF.
  Team Catalog membership is resolved independently per request and never inferred from
  request headers/bodies.
- Both standalone ingresses are removed; the service is reachable only cluster-internally
  from `reops-innblikk-frontend`.

## API

Base: `/api/manage/websites`. All routes require an authenticated Azure OBO user whose
`azp` is the Innblikk BFF client. Identity requires the validated JWT's `NAVident`
claim; email addresses are never used as an identity fallback. Machine tokens
(`idtyp=app`) and tokens without delegated scope are rejected with 401.

| Method & path                                       | Description                                                                                                                                                                                                                                                                                                                                                         |
| --------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GET /context`                                      | `{ teams: [{id,name}], isReopsTeamMember }` for the caller.                                                                                                                                                                                                                                                                                                         |
| `GET ?scope=mine\|all&includeDeleted=true\|false`   | List registrations. `scope` defaults to `mine` (caller's own teams). `all` requires ReOps membership. `includeDeleted` works for both scopes. Non-admins cannot list `all` and never see unrelated/unassigned rows.                                                                                                                                                 |
| `GET /{websiteId}`                                  | Single registration. Non-admins only see rows owned by their teams; everything else (including unassigned rows) returns 404.                                                                                                                                                                                                                                        |
| `POST` body `{name,domain,ownerTeamId,websiteId?}`  | Create. Server generates the UUID unless ReOps supplies an optional `websiteId`. An already-registered ID (including soft-deleted rows) returns 409 and is never overwritten. Returns 201 + `Location`. Non-admin: `ownerTeamId` must be one of the caller's teams and a custom `websiteId` is forbidden. ReOps may set any owner team UUID or `null` (unassigned). |
| `PUT /{websiteId}` body `{name,domain,ownerTeamId}` | Update. Non-admin must already own the row and cannot change ownership (any `ownerTeamId` in the body is ignored; the existing owner is kept). Admin may reassign to any UUID or `null`. Editing a soft-deleted row returns 409.                                                                                                                                    |
| `DELETE /{websiteId}`                               | Soft delete. Sets `deleted_at`/`updated_at` to the same timestamp, evicts caches (stopping ingestion), publishes a Kafka `DELETE` tombstone. Idempotent — a second call is a no-op with no duplicate publication. Returns 204.                                                                                                                                      |
| `DELETE /{websiteId}/registration`                  | **Hard delete (ReOps only).** Requires header `X-Confirm-Website-Id` exactly equal to the path UUID. Removes **only** the Postgres row; publishes **no** Kafka event and touches **no** session/analytics data. Accepts active registrations (supports already-purged BQ rows). Returns 204.                                                                        |

`WebsiteRegistration` JSON:

```
{websiteId, name, domain, ownerTeamId: string|null,
 createdAt: ISO8601, updatedAt: ISO8601, deletedAt: ISO8601|null}
```

### Errors

RFC 7807 `ProblemDetail` bodies:

- `400` — validation failure or missing/mismatched confirmation header.
- `401` — token missing/invalid or no delegated user identity.
- `403` — authenticated but not permitted (e.g. `scope=all` for non-admin, `ownerTeamId`
  outside caller's teams, hard delete for non-ReOps).
- `404` — missing, or hidden (unrelated/unassigned row for a non-admin).
- `409` — editing a soft-deleted registration, concurrent modification, or creating with an already-registered ID.
- `503` — Team Catalog membership lookup failed (fails closed; never assumes membership).

### Validation

- `name`: `@NotBlank`, max 100 (matches the BigQuery sink limit; tighter than the PG column).
- `domain`: `@NotBlank`, max 253; hostname only (RFC 1034/1123 labels, no scheme/port/path/trailing dot).
- Both are normalized server-side (`trim`, and `domain` lowercased) before persistence.
- No outbound fetching of the domain is performed.
- Ownership-only edits stay in Postgres and publish nothing. Name/domain edits publish
  the usual website update; assigning ownership cannot recreate purged BigQuery metadata.
- `websiteId`: optional UUID on creation, ReOps only. Omitted/null generates a new UUID.
  Supplying it on update returns 400; IDs cannot be changed.

### Recovering a hard-deleted registration

In Innblikk's `/reops-internal/nettsider`, use the normal **Registrer ny nettside**
form and fill in **Nettside-ID (valgfritt)** with the exact original UUID, plus the
original domain and desired name/owner. Leave the ID blank for normal new websites.

Hard delete removes only the `website` registration in Postgres, not session rows,
BigQuery metadata, or historical analytics. Recreating the same UUID restores the
ingestion gate and retains historical associations by `website_id`; name/domain
alone do not reconnect anything. Creation publishes the usual active `CREATE`
metadata event for downstream BigQuery latest-row readers. Metadata timestamps and
ownership belong to the new registration; previous values are not restored automatically.

Existing IDs, including soft-deleted registrations, are rejected with 409. Creation
is INSERT-only, with the primary key guarding concurrent requests too. This is not
a soft-delete restore endpoint. Events discarded while the registration was missing
are not replayed. Kafka delivery remains best-effort as documented below; success
does not guarantee downstream metadata has already arrived.

Deploy this backend before the UI/BFF change: the old backend always generates a new
UUID and cannot honor a requested ID. No database migration is needed.

Downstream caveat: the BigQuery sink appends metadata versions. Innblikk's website
list selects the latest version per ID, but the `reops-iac` Terraform definition of
`umami_views.event` joins raw `public_website` rows without deduplication. If deployed
as defined, multiple metadata versions can multiply joined event rows after any
metadata change, including recovery. Verify the live view before relying on its
counts; this patch does not change reporting infrastructure.

### Optimistic locking (lost-update protection)

The `website` table has an internal `version` column (`@Version`, V6 migration). The
controller reads an authorized snapshot (carrying `version`), then passes that expected
version to the service mutation. If the row changed between authorization and mutation —
e.g. a concurrent admin owner transfer, or a soft-delete that must not be revived — the
version check fails with `409 Conflict`, so a stale non-admin edit cannot undo it. The
version is internal and not exposed on the public registration DTO.

### Audit

Every mutation logs the server-derived actor NAVident + operation + website UUID
(`actor={} operation={} websiteId={}`). Name/domain/email/token/request bodies are never
logged.

## Authorization model

- **Authentication** is the Spring Security OAuth2 resource server with an explicitly
  wired `NimbusReactiveJwtDecoder` (see `security/SecurityConfig.kt`). The decoder
  validates signature (JWKS from the issuer), and installs a `DelegatingOAuth2TokenValidator`
  chain: default issuer (`iss`) + timestamp (`exp`/`nbf`) validators, then audience
  (`aud` must contain this app's client id), then `azp` allow-list
  (`security/AzpAllowedClientValidator.kt`, fed by `security/AllowedCallerClientIds.kt`
  which resolves the Innblikk BFF client id from the Nais-injected
  `AZURE_APP_PRE_AUTHORIZED_APPS`), then a delegated-scope check (`scp` must be present,
  which only appears in OBO user tokens — rejecting app-only tokens).
- **Identity** (`security/AuthenticatedUser.kt`) requires a real `NAVident` claim matching
  `^[A-Z][0-9]{6}$`; there is no `preferred_username` fallback, and `idtyp=app` tokens are
  rejected (401).
- **Membership/ownership** is resolved independently from Team Catalog by this service —
  never from the token or request. See `security/TeamCatalogService.kt`. Lookups are
  cached (caffeine `teamMemberships`, 10 min TTL; errors are never cached) and fail closed
  (503) on error.
- **ReOps membership** = caller is a member of the ReOps team (`app.reops-team-id`).
- `ownerTeamId` is a **Team Catalog UUID** and is intentionally distinct from the legacy
  BigQuery `team_id` filter column (see `WebsiteService.toKafkaDto`). It is **never**
  inferred from name/domain and starts as `null` for existing rows (see migration).
- Only ownership restricts **management mutations**. Browsing of all sites in Innblikk is
  a frontend concern and is unaffected.

## Blocking vs. non-blocking

The app is reactive (WebFlux) with blocking JPA. Controller methods return `Mono` and
dispatch blocking repository calls to the bounded-elastic scheduler
(`Schedulers.boundedElastic()`), so the Netty event loop is never blocked. The Team
Catalog call is non-blocking (WebClient). Authentication is non-blocking (reactive JWT
decoder).

## Cache semantics

Caffeine caches (`website/CacheConfiguration.kt`):

- `websiteAllowed` (5 min) — ingestion gate; now checks `deleted_at IS NULL` so soft
  deletes stop ingestion. Evicted on create/update/soft/hard delete, including cached
  rejections for a recreated ID. Eviction is local to the pod handling the mutation;
  other pods refresh on expiry (up to 5 min).
- `websites` (5 min, all entries evicted on any mutation) and `website` (per-id) —
  retained for compatibility; management reads use fresh repository reads.
- `teamMemberships` (10 min, max 10k) — per-NAVident team list; only successful lookups
  cached.

## Configuration

Application (`application.yaml` / `application-kubernetes.yaml`):

| Key                                                      | Source                                             | Notes                                                                                                                                                                                                                                                          |
| -------------------------------------------------------- | -------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `app.reops-team-id`                                      | `26dba481-fd96-40a8-b47d-b1ad0002bc74`             | ReOps team id in Team Catalog. Same value as `innblikk-frontend`'s `REOPS_TEAM_KATALOG_ID`.                                                                                                                                                                    |
| `app.team-catalog.base-url`                              | `https://teamkatalog-api.intern.nav.no`            | Single stable Team Catalog instance (both clusters). Matches `innblikk-frontend`.                                                                                                                                                                              |
| `app.security.pre-authorized-apps-json`                  | `${AZURE_APP_PRE_AUTHORIZED_APPS}` (Nais-injected) | The azp validator parses this JSON (`[{name:"<cluster>:<namespace>:<app>",clientId:"<uuid>"}]`) at runtime and selects the exact same-cluster/same-namespace `reops-innblikk-frontend` client id. No manual client-id config; fails closed on empty/malformed. |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri`   | `${AZURE_OPENID_CONFIG_ISSUER}` (Nais-injected)    | kubernetes profile only.                                                                                                                                                                                                                                       |
| `spring.security.oauth2.resourceserver.jwt.audiences[0]` | `${AZURE_APP_CLIENT_ID}` (Nais-injected)           | kubernetes profile only.                                                                                                                                                                                                                                       |

Nais (`.nais/nais-dev.yaml`, `.nais/nais-prod.yaml`):

- `azure.application.enabled: true`, `tenant: nav.no`, `allowAllUsers: true` for
  delegated Nav users. Team membership and resource ownership still restrict operations.
- `accessPolicy.inbound.rules` → `reops-innblikk-frontend`.
  Nais uses this policy to authorize the caller in Entra ID as well as at network level.
- `accessPolicy.outbound.external` → existing Kafka host **plus** `teamkatalog-api.intern.nav.no`.
- `ingresses` removed (was `*.ansatt.dev.nav.no` / `*.ansatt.nav.no`).

## Database migration

`V5__website_owner_team_id_and_deleted_at.sql` (additive, zero-downtime):

```sql
ALTER TABLE website ADD COLUMN owner_team_id UUID;   -- Team Catalog owner; NULL = unassigned
ALTER TABLE website ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;  -- NULL = active
CREATE INDEX idx_website_owner_team_id ON website(owner_team_id);
```

`V6__website_version.sql` (additive, zero-downtime):

```sql
ALTER TABLE website ADD COLUMN version BIGINT NOT NULL DEFAULT 0;  -- optimistic locking
```

Existing rows keep `owner_team_id = NULL` (never inferred). `hibernate ddl-auto: validate`
requires the entity to match — see `WebsiteEntity`. Verified against H2 in PostgreSQL mode
via `EndeTilEndeTest`.

## Kafka

`no.nav.reops.event.Website` gains an optional `deletedAt: Instant? = null` (additive).
`CREATE`/`UPDATE` omit it; soft-delete `DELETE` events carry `deletedAt` equal to
`updatedAt` (the deletion time). Hard delete publishes nothing. `ownerTeamId` is **not**
part of the Kafka payload (that is a separate downstream schema change). The sink
(`reops-enriched-to-bigquery`) already has the matching optional `deletedAt`.

**Residual delivery limitation:** Kafka publication from this service is best-effort
async — a failed send is logged but not retried or queued (no outbox). This is a known
limitation; no automatic historical purge or reconciliation is introduced by this change.

## Rollout (per contract)

1. Sink tombstone support + Innblikk BQ latest-row filtering (separate change).
2. This service: secure API + migration + network policies.
3. Innblikk management UI/BFF.
4. Remove old ingresses/static UI in a coordinated deployment.

Caller client IDs resolve from Nais-injected `AZURE_APP_PRE_AUTHORIZED_APPS`; no manual
client-ID variable is required. Verify this runtime mapping during rollout. No live
deploys or DB changes are performed by this task; the historical BQ purge remains a
separate human-approved operation.

Reference: [Nais Entra ID access policies](https://docs.nais.io/auth/entra-id/how-to/secure/).

## Health probes

Actuator stays at `/internal/**` (permitAll): liveness/readiness `/internal/health`,
Prometheus `/internal/prometheus` — unchanged and still reachable.
