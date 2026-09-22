# T05-B — Mapa y backfill de pertenencia (S05, cierra W2)

Parent spec: S05. Preconditions: T05-A + U01/U15.

## What was added

- `iam.api.LegacyCompanyDirectory` (public seam) exposing legacy buyer companies and
  `organizationIdForRuc(String)`. Impl `LegacyCompanyDirectoryImpl` reads `BuyerCompanyRepository`
  and `OrganizationRepository` inside `iam` (no cross-module internals leaked).
- `equipment` aggregate `QuarantinedCompanyMapping` + repository, table
  `customer_mapping_quarantines` (unique `legacy_company_id`), migration
  `V8__customer_mapping_quarantine.sql` (validated on MySQL 8.0.46).
- `CustomerBackfillService.run()` → `CustomerBackfillReport(legacyTotal, newlyMapped, alreadyMapped,
  newlyQuarantined, alreadyQuarantined)`.

## Backfill rule (conservative, no inference)

For each legacy buyer company:

1. already mapped (`customer_accounts.legacy_company_id`) → counted, skipped;
2. already quarantined → counted, skipped;
3. RUC resolves to **exactly one** organization → create a `CustomerAccount` preserving the legacy
   company id;
4. otherwise (no RUC, RUC that resolves to no organization, or registration conflict) → written to
   **quarantine** with an explicit reason (`NO_ORGANIZATION_FOR_RUC`, `REGISTRATION_CONFLICT`).

Never inferred from favourite provider or last order. Re-running is idempotent: second run maps 0 new
rows and reports the already-resolved counts, so totals reconcile
(`legacyTotal = mapped + quarantined`).

The backfill runs as an ops/service task (no REST endpoint yet, to avoid an unreachable admin route);
`CustomerBackfillTest` drives it end to end.

## Tests

`CustomerBackfillTest`: one unambiguous company is mapped to its organization's customer, one
ambiguous company is quarantined, and a second run is a no-op with stable counts.

Build green; ArchUnit baseline unchanged.

## Asunciones abiertas

- **A1 (U01/U15) — RUC is the only mapping key.** Any company whose RUC does not identify exactly one
  organization goes to quarantine rather than being guessed. If the business wants another key
  (contact email, membership owner), we revisit the report.
- **A2 — Quarantine is terminal until manually cleared.** No automatic retry/approval workflow (the
  "aprobar" half of the ticket) is automated; entries are reviewed by ops.
- **A3 — W2 closes with rows classified (mapped or quarantined), not necessarily 100 % mapped**, which
  is the S05 Definition of Done.
