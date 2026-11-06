# Third-party data notices

## IPtoASN country datasets

The build downloads the current IPv4 and IPv6 country datasets from
[IPtoASN](https://iptoasn.com/), maintained by Frank Denis:

- https://iptoasn.com/data/ip2country-v4-u32.tsv.gz
- https://iptoasn.com/data/ip2country-v6.tsv.gz

The provider publishes these datasets under the
[Open Data Commons Public Domain Dedication and License (PDDL) v1.0](https://opendatacommons.org/licenses/pddl/1-0/).
PDDL does not require attribution in consuming applications or dashboards.
This notice records provenance; it does not add downstream attribution obligations.

Downloaded datasets are packaged in the application, not committed to Git.
`geoip/metadata.properties` records each download's source, retrieval time,
provider Last-Modified header, SHA-256 checksum, and row count.

## Historical DB-IP IP to Country Lite

Earlier revisions of this repository contain `dbip-country-lite.mmdb`.
That database is by [DB-IP](https://db-ip.com/), sourced from
https://db-ip.com/db/download/ip-to-country-lite and licensed separately under
[Creative Commons Attribution 4.0 International (CC BY 4.0)](https://creativecommons.org/licenses/by/4.0/).
It is not covered by the application's MIT license. The database is no longer
used or packaged by the current application. Its original modification history
has not been established.
