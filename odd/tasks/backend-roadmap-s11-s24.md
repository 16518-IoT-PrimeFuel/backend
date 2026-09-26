# Backend roadmap S11-S24

## Objective

Complete the backend migration gates from S11 through S24 without breaking the
existing v1 compatibility surface.

## Checklist

- [x] S11-S13: supply availability, fleet eligibility, reservations and release
- [x] S14-S15: arrival and delivery assignment lifecycle
- [x] S16-S18: tracking, geofence evidence and valve ACK ledger
- [x] S19-S21: outbox relay, notifications and delivery timeline
- [x] S22-S23: coexistence controls and payment boundary
- [x] S24: retirement register and migration documentation
- [x] Run migration, architecture and application test gates
- [x] Audit the local Mobile-app consumer and record v1/static-mock evidence
- [ ] Migrate Mobile-app to executable v2 consumers and confirm deployed consumers
- [ ] Retire v1 routes and legacy tables only after the consumer ledger is closed

## Verification

`git diff --check` and the Maven suite pass with the installed JDK 25 using
`-Dmaven.compiler.release=25`; the repository target remains JDK 26.

## Scope note

S22/S24 remain controlled-retirement gates because the local Mobile-app still
contains v1 clients with mock-default providers and the roadmap requires
deployed-consumer evidence before destructive legacy removal.
