# Privacy Filter – Design and Rules

This privacy filter is implemented as a small pipeline of independent components.
Each component has exactly one responsibility, making the system easy to understand,
extend, and maintain.

---

## Architecture Overview

```
Event
 └─ FilterService
      ├─ KeyPolicy   → structural decisions (drop / preserve / force)
      ├─ UrlPolicy   → context decisions (what is a URL, path vs query behavior)
      ├─ Redactor    → regex-based value redaction (+ preservation placeholders)
      └─ Traverser   → recursive traversal + orchestration
```

| Component       | Responsibility                                                                       |
|-----------------|--------------------------------------------------------------------------------------|
| `FilterService` | Composition root. Wires everything together and applies policies to an event.        |
| `KeyPolicy`     | Key-based business rules: drop fields, preserve trusted values, force anonymization. |
| `UrlPolicy`     | Defines what is a URL field and how URLs are sanitized (path vs query).              |
| `Redactor`      | Applies regex rules and preservation/restoration (URL-like substrings).              |
| `Traverser`     | Walks nested data structures and delegates decisions to the policies.                |

Responsibilities are isolated:
- Structural rules do not know about regexes.
- Regex rules do not know about traversal.
- URL behavior is contained in one place.

---

## Data Flow

1. `FilterService` receives an `Event`
2. The static **default filter** (`DefaultPolicies.defaultFilter`) determines which
   keys are excluded from redaction (see [Default Filter](#5-default-filter-excluded-keys) below)
3. Payload header fields are copied, with redaction applied to every field
   **not** in the default filter:
   - `payload.id`, `payload.hostname`, `payload.screen`, `payload.language`,
     `payload.title`, `payload.url`, `payload.referrer`, `payload.name`
4. `UrlPolicy` sanitizes URL-like values (path vs query handling)
5. `Traverser` walks `payload.data` recursively:
   - Applies `KeyPolicy` for structural decisions
   - Uses `UrlPolicy` to detect URL contexts / URL-like keys
   - Uses `Redactor` to sanitize string values
   - Skips keys present in the default filter
6. The sanitized event is returned

All behavior is deterministic and enforced by the test suite.

---

## Filter Model

The filter is built around **three independent layers**:

1. Structural transforms (key-based behavior)
2. URL policy (context-based handling)
3. Redaction rules (value-based detection)

Each layer solves a different problem and does not depend on the others.

---

## 1. Structural Transforms (Key-based rules)

These rules are applied based on the **field name**, before any regex-based redaction.

| Key / Condition                                                                                    | Behavior                   | Output              |
|----------------------------------------------------------------------------------------------------|----------------------------|---------------------|
| `ip_address`                                                                                       | Remove field completely    | *(key is dropped)*  |
| `ip`                                                                                               | Replace with remote marker | `"$remote"`         |
| `api_key`                                                                                          | Always preserved           | unchanged           |
| `device_id`                                                                                        | Always preserved           | unchanged           |
| `website`                                                                                          | Always preserved           | unchanged           |
| Advertising IDs:<br>`idfa`, `idfv`, `gaid`, `adid`, `android_id`, `aaid`, `msai`, `advertising_id` | Force anonymization        | `"[PROXY]"`         |
| Any map / list / set / array                                                                       | Traversed recursively      | structure preserved |

**Purpose:**  
Structural rules express business decisions about what is trusted, dropped, or forced
to anonymized values. No regex is involved here.

---

## 2. URL Policy (Context-based handling)

The filter distinguishes between:

- **Strict URL fields** (known URL semantics): handled as URLs.
- **URL-like keys** (navigation/link fields): handled as URLs when the value is a string.

| Location / Key (string values)                                                                                                                              | URL handling                                                        |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| `payload.url`                                                                                                                                               | Treated as URL                                                      |
| `payload.referrer`                                                                                                                                          | Treated as URL                                                      |
| `payload.data.payload.url`                                                                                                                                  | Treated as URL *(nested inside `payload.data`)*                     |
| `payload.data.payload.referrer`                                                                                                                             | Treated as URL *(nested inside `payload.data`)*                     |
| URL-like keys anywhere in `payload.data`, such as `url`, `href`, `path`, `pathname`, `link`, `destination`, `lenkesti`, `newLocation`, `prevLocation`, etc. | Treated as URL (path vs query handling; filepath exclusion on path) |
| Any other field                                                                                                                                             | Treated as a normal string                                          |

For URL handling:

1. The value is split into:
   - **Path-part** (before `?`)
   - **Query string** (from `?` and onward)
2. Redaction is applied to both parts, but with different rule sets.

### URL rule exclusions

| URL section  | Disabled rules   | Why                                                               |
|--------------|------------------|-------------------------------------------------------------------|
| Path-part    | `PROXY-FILEPATH` | Prevent false positives where URL paths resemble filesystem paths |
| Query string | *(none)*         | Query strings often contain real PII and must be fully sanitized  |

**Purpose:**  
URL paths are treated as identifiers and routing data.  
Query strings are treated as user input and are fully sanitized.

---

## 3. Redaction Rules (Value-based, Regex)

These rules apply to all string values unless explicitly excluded (e.g. URL path-part excludes `PROXY-FILEPATH`).

| Rule                | Label                  | Detects                                        | Output                   |
|---------------------|------------------------|------------------------------------------------|--------------------------|
| UUID                | `PROXY-UUID`           | UUIDs (`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`) | `[PROXY-UUID]`           |
| File path           | `PROXY-FILEPATH`       | Filesystem paths                               | `[PROXY-FILEPATH]`       |
| National ID (FNR)   | `PROXY-FNR`            | 11-digit Norwegian IDs                         | `[PROXY-FNR]`            |
| NAV ident           | `PROXY-NAVIDENT`       | `X123456` format                               | `[PROXY-NAVIDENT]`       |
| Email               | `PROXY-EMAIL`          | Email addresses                                | `[PROXY-EMAIL]`          |
| IP address          | `PROXY-IP`             | IPv4 strings                                   | `[PROXY-IP]`             |
| Phone               | `PROXY-PHONE`          | 8-digit numbers                                | `[PROXY-PHONE]`          |
| Name                | `PROXY-NAME`           | Full-name patterns (space or dot separated)    | `[PROXY-NAME]`           |
| Address             | `PROXY-ADDRESS`        | Postal address-like patterns                   | `[PROXY-ADDRESS]`        |
| Secret address      | `PROXY-SECRET-ADDRESS` | “hemmelig adresse”                             | `[PROXY-SECRET-ADDRESS]` |
| Account number      | `PROXY-ACCOUNT`        | `1234.56.78901` format                         | `[PROXY-ACCOUNT]`        |
| Organization number | `PROXY-ORG-NUMBER`     | 9 digits                                       | `[PROXY-ORG-NUMBER]`     |
| License plate       | `PROXY-LICENSE-PLATE`  | `AB12345` format                               | `[PROXY-LICENSE-PLATE]`  |
| Search query        | `PROXY-SEARCH`         | `?q=`, `?search=` etc                          | `[PROXY-SEARCH]`         |

---

## 4. Preserved Patterns

Some values are temporarily replaced with placeholders to avoid accidental redaction,
then restored unchanged after rule application.

| Pattern                              | Reason                                                                                            |
|--------------------------------------|---------------------------------------------------------------------------------------------------|
| URL-like substrings (free-text only) | Avoid redacting legitimate routing/link text inside normal strings (not `payload.url`/`referrer`) |
| `nav123456`, `test654321`            | Explicitly allowed identifiers (matched by a "keep" rule and not replaced)                        |

Notes:
- UUIDs are **redacted** (`[PROXY-UUID]`) on all fields. The only exception is the `payload.website` field
  (a typed `UUID`, passed through without redaction) and the `website` key inside `payload.data`
  (structurally preserved via `KeyPolicy`).
- URL-like preservation is **not** used for strict URL fields; URLs are redacted via `UrlPolicy` split (path vs query).
- The "keep" identifiers are detected so other redaction rules don't accidentally proxy them.

---

## 5. Default Filter (Excluded Keys)

The default filter is a static set of keys defined in `DefaultPolicies.defaultFilter`.
Fields whose key appears in this set are **excluded from redaction** — their values are
passed through unchanged. This applies both to top-level payload fields and to keys
encountered during recursive traversal of `payload.data`.

The current default filter keys:

| Key                  | Purpose                           |
|----------------------|-----------------------------------|
| `komponent`          | Component name                    |
| `lenketekst`         | Link text                         |
| `breadcrumbs`        | Navigation breadcrumbs            |
| `pageType`           | Page classification               |
| `pageTheme`          | Page theme                        |
| `employer`           | Employer flag                     |
| `seksjon`            | Section name                      |
| `valg`               | User selection                    |
| `jobTitle`           | Job title                         |
| `occupationLevel2`   | Occupation category               |
| `enhet`              | Organizational unit               |
| `filter`             | Applied filter                    |
| `organisasjoner`     | Organization count/flag           |
| `destinasjon`        | Destination identifier            |
| `location`           | Location identifier               |
| `arbeidssted`        | Workplace                         |
| `kilde`              | Source identifier                  |
| `skjemanavn`         | Form name                         |
| `lenkegruppe`        | Link group                        |
| `linkText`           | Link text (English variant)       |
| `descriptionId`      | Description identifier            |
| `tema`               | Topic                             |
| `innholdstype`       | Content type                      |
| `yrkestittel`        | Job title (Norwegian)             |
| `tlbhrNavn`          | Navigation element name           |

To add or remove a key from the default filter, edit `DefaultPolicies.defaultFilter`
in `FilterService.kt`.

---

## How to Extend

| Change you want              | Where to implement              |
|------------------------------|---------------------------------|
| Preserve a new key           | `KeyPolicy`                     |
| Drop a new key               | `KeyPolicy`                     |
| Force-anonymize a key        | `KeyPolicy`                     |
| Change URL behavior          | `UrlPolicy`                     |
| Add a new PII regex          | `FilterService.buildRules()`    |
| Change traversal behavior    | `Traverser`                     |
| Exclude a key from redaction | `DefaultPolicies.defaultFilter` |

Most changes should touch exactly one file.
It acts as a deterministic and auditable privacy firewall.
---
