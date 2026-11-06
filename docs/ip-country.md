# Country enrichment

The application resolves country only, not city, postcode, or coordinates.
It uses the official [IPtoASN](https://iptoasn.com/) IPv4 and IPv6 country datasets,
published under PDDL v1.0. No UI attribution is required under that license.
See [third-party data notices](../src/main/resources/THIRD-PARTY-NOTICES.md).

## Fresh data on every build

`./gradlew bootJar`, `build`, `test`, and `bootRun` prepare resources through
`fetchIpCountry`. That task downloads both datasets afresh, with HTTP caching
disabled and no Gradle up-to-date or build-cache reuse. It validates gzip/TSV
contents and rejects empty responses. Download or validation failures fail the
build; previous downloads are never used as a fallback.

The generated gzip files and `geoip/metadata.properties` live under `build/`
and are included in the application JAR/image, never committed to Git. Metadata
records source URLs, fetch times, provider Last-Modified headers, SHA-256 hashes,
and row counts. Tests for range boundaries and malformed data use inline fixtures;
service smoke tests also exercise the freshly downloaded datasets.

IPtoASN advertises hourly updates. Fetching the current published snapshot does
not guarantee the provider has produced a new snapshot each hour; inspect its
Last-Modified headers in the packaged metadata when diagnosing stale results.

## Production refresh

The existing daily 03:00 UTC workflow checks out the latest production tag,
rebuilds the JAR/image, and redeploys it. Once this change has been promoted to a
production tag, those rebuilds fetch current country data without changing the
application source or requiring runtime network access. The workflow's Docker
cache settings are separate from the explicitly uncached Gradle download task.

## Runtime behavior and limitations

At startup the application loads both datasets into sorted IP-range indexes and
validates bounds, ordering, non-overlap, and country codes. Lookups use binary
search and never make network requests. Hostnames are rejected without DNS
lookups; private, loopback, link-local, multicast, missing, and unknown addresses
return no country. Only the first X-Forwarded-For address is used.

IPtoASN country mappings are approximate network-derived data, not proof of a
visitor's physical location. VPNs, proxies, and multinational networks can affect
results. Country-only enrichment reduces location granularity but does not make
events anonymous: session fingerprinting and other identifiers are separate.
