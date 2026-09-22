# T19-B — Consumer idempotency (inbox) — minimal (S19)

Parent spec: S19. Precondition: T19-A.
Scope: **minimal consumer-side dedup** only. This closes W1; the full at-least-once operational
machinery is intentionally deferred.

## What was added

- `shared.events.EventInbox` SPI: `boolean consume(String consumer, String eventId)` — `true` the
  first time a `(consumer, eventId)` pair is seen, `false` afterwards.
- `JpaEventInbox` implementation (`@Transactional`).
- Table `consumed_events` (`V4__consumed_events.sql`), unique `(consumer, event_id)`.
  Validated under Hibernate `validate` on local MySQL 8.0.46.
- `EventInboxTest`: an event is consumed once per consumer, and different consumers each get it once.

## Deliberately deferred (not in this ticket)

- Dispatcher/poller that actually delivers publications and sets `event_publications.completed_at`.
- Replay, backlog/age metrics, poison-event handling, DLQ.
- Concurrent-duplicate safety: the current check is `exists`-then-`save` (the unique constraint is the
  backstop); catching the constraint violation in-transaction without poisoning the TX is deferred.

Build: 39/39 green.
