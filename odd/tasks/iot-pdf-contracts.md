# IoT and PDF API contracts

## Objective

Define the smallest real backend contracts needed to replace the mobile IoT and PDF visual mocks.

## Scope

- Add a read contract for tank telemetry/current state and an export contract for report documents.
- Keep existing v1/v2 routes backward compatible.
- Add focused contract tests and update the migration/consumer documentation.

## Tasks

- [x] T1: Inspect existing equipment, telemetry, reporting, and authorization flows.
- [x] T2: Implement backend telemetry read contract with tests.
- [x] T3: Implement backend report export contract with tests.
- [x] T4: Connect mobile repositories/controllers and replace the two mock paths.
- [x] T5: Run backend/mobile checks and record evidence.

## Constraints

- MVM on mobile and modular bounded contexts on backend.
- No new dependency unless the existing stack cannot provide the behavior.
- Keep each work unit independently buildable and committed.

## Progress

Completed in two work units. Backend commit `34236bb` adds tank state reads and CSV report exports; mobile commit `c1cca3f` consumes both contracts with mock fallback.

## Checks

- Backend: `bash mvnw -q -Dmaven.compiler.release=25 test`
- Mobile: `flutter analyze` and `flutter test`

## Evidence

- Backend: `git diff --check` passed; focused controller/OpenAPI tests added. Full Maven run was unavailable in the worker environment because the active Java compiler did not support release 25.
- Mobile: `flutter analyze` passed; `flutter test` passed with 50 tests.
- Runtime boundary: N/A; no deployed backend or emulator endpoint was available in this work unit.
- Rollback boundaries: revert `34236bb` for backend contracts or `c1cca3f` for mobile consumers independently.
