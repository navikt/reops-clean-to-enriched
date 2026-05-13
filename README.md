# reops-clean-to-enriched
Kafka-konsument som beriker webanalyse-hendelser — bot-filtrering, GeoIP-landsoppslag, User-Agent-parsing (nettleser/OS/enhet), informasjonskapselefri sesjonsfingeravtrykk, URL-normalisering, språknormalisering og hendelsesdatatyping.

## Kafka-topics

| Reads | Writes            | Retry/DLT (via beans)  |
|-------|-------------------|------------------------|
| clean | enriched, website | clean-retry, clean-dlt |

## Forutsetninger
- Java 25

## Bygg
```
./gradlew build
```

## Kjør lokalt
1. Start avhengighetene via docker-compose i `reops-event-proxy`-prosjektet (under `.compose`)
2. Kjør med `local`-profilen (deaktiverer Kafka SSL):
   ```
   ./gradlew bootRun -Dspring-boot.run.profiles=local
   ```
