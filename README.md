# reops-clean-to-enriched
Kafka-konsument som beriker webanalyse-hendelser — bot-filtrering, GeoIP-landsoppslag, User-Agent-parsing (nettleser/OS/enhet), cookiefri sesjonsfingeravtrykk, URL-normalisering, språknormalisering og hendelsesdatatyping.

Produserer berikede hendelser til Kafka og lagrer sesjons- og nettsteddata i PostgreSQL (brukt av Umami).

## Kafka-topics

| Leser                         | Skriver                       | Retry/DLT (via beans)                             |
|-------------------------------|-------------------------------|---------------------------------------------------|
| team-researchops.analytics-clean | team-researchops.analytics-enriched, team-researchops.analytics-website | analytics-clean-retry, analytics-clean-dlt |

## Arkitektur

```
analytics-clean (Kafka)
        │
        ▼
  EventEnrichment
   ├── IPtoASN-oppslag → land
  ├── User-Agent-parsing → nettleser / OS / enhet
  ├── Sesjons-ID (deterministisk hash: website + hostname + IP + UA + måned + salt)
  ├── Besøks-ID (deterministisk hash: sesjon + time)
  ├── URL-normalisering (path + query, ingen host)
  ├── Språknormalisering (f.eks. "nb-NO" → "nb")
  └── Hendelsesdatatyping (string / number)
        │
        ├──▶ analytics-enriched (Kafka) — berikede hendelser
        ├──▶ analytics-website (Kafka) — nettstedsoppslag
        └──▶ PostgreSQL — sessions + website-tabeller (Caffeine-cache foran)
```

### Sesjonshåndtering
- Sesjon-ID genereres deterministisk (ingen cookies) og roteres månedlig
- Besøks-ID roteres per time (speiler Umamis logikk)
- `SessionService` sjekker Caffeine-cache → DB før ny sesjon lagres
- `identify`-hendelser lagres som `session_data` (ikke `event_data`)

### Nettstedshåndtering
- Nettsteder cachet i minnet (Caffeine) med kort TTL
- Sikret REST-endepunkt for administrasjon: `WebsiteManagementController` under
  `/api/manage/websites` (Azure AD OBO + Team Catalog-eierskap). Se
  [`docs/website-management.md`](docs/website-management.md) for API, konfigurasjon og
  utrullingsnotater.
- Den gamle statiske UI-en og de uautentiserte `/api/websites`-endepunktene er fjernet.

## Forutsetninger
- Java 25
- GraalVM (for native image-bygg)
- Docker (for lokal kjøring av avhengigheter)

## Bygg
```bash
./gradlew build
```

Native image (krever GraalVM):
```bash
./gradlew nativeCompile
```

## Kjør lokalt
1. Start avhengighetene via docker-compose i `reops-event-proxy`-prosjektet (under `.compose`)
2. Kjør med `local`-profilen (deaktiverer Kafka SSL):
   ```bash
   ./gradlew bootRun -Dspring-boot.run.profiles=local
   ```

## Teknisk stack
- Spring Boot 4 / Kotlin
- Spring Kafka (konsument med retry + DLT)
- Spring Data JPA + PostgreSQL 18 (NAIS Cloud SQL)
- Flyway (DB-migrasjoner)
- Caffeine (in-memory cache)
- IPtoASN country data (PDDL; fetched on every build, local IPv4/IPv6 lookup)
- GraalVM Native Image
- NAIS (Kubernetes, Aiven Kafka, autoskalering basert på Kafka-lag)

## Country data

Country datasets are downloaded fresh during each build and bundled in the
application image, not committed to Git. The nightly production rebuild refreshes
them after this change is promoted. No runtime download or dashboard attribution
is required. See [country enrichment](docs/ip-country.md) and
[third-party data notices](src/main/resources/THIRD-PARTY-NOTICES.md).

## Deployment

Pushes to main test and deploy to dev. Prod promotion is manual. Daily refreshes
rebuild the latest tagged dev/prod releases, not main HEAD. Manual deployments
also support previous release tags for redeployment or rollback. See
[deployment behavior and release selection](docs/deployment.md).
