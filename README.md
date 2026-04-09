# reops-user-events
En Kafka-basert pipeline for prosessering av webanalyse-hendelser. Rå sporingshendelser går gjennom redigering, berikelse og inn i lagring — erstatter [Umami](https://umami.is/) for hendelsesprosessering, mens OpenSearch og BigQuery brukes til lagring og spørring.

## Arkitektur

```
Rå hendelser (Kafka)
       │
   [scrubs]  ── Fjerner personopplysninger (fødselsnummer, e-post, IP-er, osv.)
       │
Rene hendelser (Kafka)
       │
   [umami]   ── Beriker med GeoIP, User-Agent-parsing, sesjons-ID-er, bot-filtrering
       │
Berikede hendelser (Kafka)
       ├──→ [opensearch]  ── Indekserer hendelser for søk og dashbord
       └──→ [bigquery]    ── Strømmer hendelser til BigQuery for langtidsanalyse
```

## Moduler
| Modul          | Beskrivelse                                                                                                                                                                                                                    |
|----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **contract**   | Delte datamodeller (`Event`, `EnrichedEvent`, osv.) brukt av alle moduler                                                                                                                                                      |
| **scrubs**     | Kafka-konsument som fjerner personopplysninger — oppdager og maskerer fødselsnummer, e-postadresser, telefonnummer, IP-adresser, kontonummer og mer ved hjelp av regex-mønstre                                                 |
| **umami**      | Kafka-konsument som beriker hendelser — bot-filtrering, GeoIP-landsoppslag, User-Agent-parsing (nettleser/OS/enhet), informasjonskapselefri sesjonsfingeravtrykk, URL-normalisering, språknormalisering og hendelsesdatatyping |
| **opensearch** | Kafka-konsument som masseindekserer berikede hendelser i OpenSearch via gRPC med daglig roterte indekser                                                                                                                       |
| **bigquery**   | Kafka-konsument som strømmer berikede hendelser og nettstedsmetadata til BigQuery ved hjelp av Storage Write API                                                                                                               |

## Kafka-topics

| App        | Reads             | Writes            | Retry/DLT (via beans)     |
|------------|-------------------|-------------------|---------------------------|
| scrubs     | raw               | clean             | raw-retry, raw-dlt        |
| umami      | clean             | enriched, website | clean-retry, clean-dlt    |
| bigquery   | enriched, website | —                 | bigquery-dlt              |
| opensearch | enriched          | —                 | opensearch-dlt            |

## Forutsetninger
- Java 25

## Bygg
```
./gradlew build
```

## Kjør lokalt
1. Start avhengighetene via docker-compose i `reops-event-proxy`-prosjektet (under `.compose`)
2. Kjør en modul med `local`-profilen (deaktiverer Kafka SSL):
   ```
   ./gradlew :umami:bootRun -Dspring-boot.run.profiles=local
   ```
