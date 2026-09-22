# T02-A — Module dependency rules and enforcement baseline (S02)

Parent spec: S02 — Establecer fronteras verificables de módulos.
Precondition: T01-B (`docs/api-ledger/T01-B-state-security-characterization.md`).
Scope: this ticket only *establishes the enforcement mechanism and freezes the current state*. It
does not move any code between modules and does not introduce the target `api/`/`events` surfaces —
that is T02-B (first real seam) onward. No file under `src/main/java` was changed.

## Decision: enforcement tool

**Chosen: ArchUnit 1.5.0, test scope, JUnit 5 (plain `archunit` core artifact).**

Roadmap S02 explicitly leaves the tool open: "Spring Modulith solo con versión estable compatible
verificada" and T02-A "debe fijar una versión estable compatible o elegir una regla arquitectónica
equivalente". This project is on **Spring Boot 4.0.6 / Java 26** (class file major version 70).

| Option | Verdict for this project |
|---|---|
| Spring Modulith | The Boot-4 line was still milestone/snapshot at decision time (`spring-modulith` 2.2.0-M1 style releases). The roadmap does not want the rest of the DAG tied to an unvalidated version (Risk **R13**). Deferred, not rejected — can be reconsidered in a later ticket once a stable Boot-4-compatible line ships. |
| **ArchUnit 1.5.0** | **Chosen.** Stable release (2026-08-05). Release notes: 1.4.2 added "Support Java 26 / class file major version 70"; 1.5.0 adds Java 27 / major 71. No Spring/Boot coupling — pure bytecode analysis, so it cannot break with a Boot upgrade. Verified at runtime: `PluginLoader -- Detected Java version 26.0.2`. |

Using the plain `com.tngtech.archunit:archunit` core artifact (not `archunit-junit5`) so the tests
are ordinary JUnit Jupiter `@Test` methods and stay independent of the JUnit platform version that
Boot 4 tracks.

## What was added

- `pom.xml` — `com.tngtech.archunit:archunit:1.5.0`, `<scope>test</scope>`.
- `src/test/resources/archunit.properties` — points the freeze store at
  `src/test/resources/archunit_store` and sets `freeze.store.default.allowStoreCreation=false`
  (a missing/committed baseline is a hard failure, not a silent re-freeze).
- `src/test/resources/archunit_store/` — the frozen baseline (9 rule entries + `stored.rules` index).
- `src/test/java/.../architecture/ModuleBoundaryRulesTest.java` — the rules.
- `src/test/java/.../equipment/architecturefixture/SeededBoundaryViolation.java` — a test-only class
  that deliberately reaches into another module's internals, used to prove the rule is not vacuous.

## Rules

Modules are the 10 direct sub-packages of `com.primefuel.fulltank.platform`:
`catalog, equipment, fulfillment, iam, inventory, notification, ordering, payment, reporting` (+ the
`shared` kernel).

1. **Boundary rule** (`moduleMustNotReachIntoAnotherModulesInternals`): a class in module *M* must
   not depend on any class in another module *N*'s **`domain`**, **`infrastructure`**, or
   **`application.internal`** packages. Cross-module access through `interfaces.*`, `shared.*` and
   `application.{command,queryservices}` (the de-facto public service interfaces today) remains
   allowed for now — those are tightened to `api`/`events` in later tickets, not here.
2. **Shared kernel rule**: `shared` must not depend on any business module. Currently satisfied
   (0 violations) — it is a plain, unfrozen check that fails the day `shared` grows a business import.

The boundary rule is wrapped in `FreezingArchRule`: the current violations are the **baseline**, and
the test fails **only on new violations**. `FreezingArchRule` also drops entries that get solved, so
the baseline can only shrink.

## Frozen baseline (current state)

185 dependency lines across 8 modules (`iam` is clean — it is a pure provider):

| Module | Frozen violations |
|---|---|
| catalog | 6 |
| equipment | 26 |
| fulfillment | 47 |
| iam | 0 |
| inventory | 5 |
| notification | 17 |
| ordering | 27 |
| payment | 16 |
| reporting | 41 |

The most relevant crossings for the next tickets: `equipment`→`inventory.domain.FuelType` and
`*`→`iam.infrastructure...CurrentUserAccess` (7 modules) — the latter is exactly what T02-B replaces
with an `iam.api` access seam; and `payment`→`ordering.domain.FuelOrderRepository` +
`reporting`→multiple modules' internals — later W7/tracking targets.

## Evidence / acceptance

- **A new violation fails the build.** Verified empirically: a temporary `catalog` class importing
  `payment.domain.repositories.PaymentRepository` (a target not present in the baseline) made
  `moduleBoundariesRespectTheFrozenBaseline` fail with exactly those 2 new dependency lines, while
  every pre-existing baseline violation stayed filtered out. The temporary class was then removed
  and the rule went green again.
- **The rule is not vacuous.** `boundaryRuleDetectsASeededViolation` imports the
  `SeededBoundaryViolation` fixture and asserts the (unfrozen) rule reports a violation.
- **Test-only classes never leak into the baseline.** `DO_NOT_INCLUDE_TESTS` is applied on the
  production import; the store contains no `SeededBoundaryViolation` / `architecturefixture` entry
  (verified by grep).
- **Build green.** `./mvnw test` (Java 26.0.2) — 33/33 tests pass (30 previous + 3 architecture).

### Acceptance criteria check (Roadmap T02-A / S02)

- Regla falla con violación sembrada: **yes** (seeded-in-`src/main` run shown above, plus the fixture test).
- Versión estable compatible documentada: **yes** (ArchUnit 1.5.0, Java 26 verified at runtime).
- Módulos/imports modelados y baseline congelado: **yes** (185 lines, decreasing-only baseline).
- Baseline no crece: **yes** — `FreezingArchRule` fails on any new violation.
- `shared` no importa negocio: **yes** — explicit rule, currently 0 violations.

## How to change the baseline intentionally

- To *shrink* it: fix a crossing; the failing entry is removed automatically on the next green run.
- To *regenerate* after an intentional, reviewed change: temporarily set
  `freeze.store.default.allowStoreCreation=true` (or run with `-Darchunit.freeze.refreeze=true`),
  run the tests once, review the `git diff` of `archunit_store/`, then commit the store and revert
  the property.

## Limitations / follow-ups

- Only `domain` / `infrastructure` / `application.internal` are restricted for now. The remaining
  cross-module `interfaces.*` and `application.{command,queryservices}` couplings are *allowed* until
  the `api`/`events` surfaces exist (T02-B+), at which point the rule should be tightened to
  "modules depend on other modules only via `api`/`events`".
- Spring Modulith remains an option to revisit if a stable Boot-4-compatible release is verified;
  nothing in the current rules depends on it.
