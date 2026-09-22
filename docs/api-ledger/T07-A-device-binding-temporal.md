# T07-A — Modelo temporal de DeviceBinding (S07)

Parent spec: S07. Preconditions: T06-B, T04-B.

## What was added (equipment, package `equipment.devicebinding`)

- Aggregate `DeviceBinding` with a **half-open validity window** `[validFrom, validTo)`:
  `covers(instant)` / `overlaps(from, to)` make attribution a function of the reading's instant, never of
  "whatever is bound now". `validTo == null` means open.
- Invariants: a device id, a channel and a tank are required; only an open binding can be closed; the
  closing instant cannot precede the validity start.
- **Two layers against duplicate open bindings**:
  1. domain/service check — a new period that overlaps any existing period of the same
     (deviceId, channel) is a conflict;
  2. database constraint — `active_slot` is `1` while open and `NULL` once closed, and
     `uk_device_bindings_open_channel UNIQUE (device_id, channel, active_slot)` makes two open bindings
     impossible (a plain UNIQUE index ignores NULLs, so history is unconstrained).
- Operations: `bind`, `revoke` (status `REVOKED`), `moveTo` (closes the current period and returns the
  next binding, raising `DeviceMoved`).
- Domain events `DeviceBoundEvent` / `DeviceRevokedEvent` / `DeviceMovedEvent` carry **no credential**
  (only device, channel, tank, organization, instants).
- Public seam `equipment.api.ActiveBinding.activeAt(deviceId, channel, instant)` returning the tenant
  (`organizationId`) and tank, so "no deviceId without verifiable tenant/binding" (S07 DoD) is enforceable
  by other modules.
- No client route is added: S07 defines a technical provisioning interface, not a customer API.

## Tests

`DeviceBindingTemporalTest`:
- second open binding for the same channel refused by the domain check;
- overlapping historical period refused;
- **database constraint is the backstop**: saving a second open binding directly through the repository
  raises `DataIntegrityViolationException`;
- **time boundary**: the closing instant is already outside the binding (`T1-1s` present, `T1` empty);
- **move**: `T0..T1` stays on the old tank, `T1` onwards resolves to the new tank, and moving an already
  closed binding is a conflict;
- events are raised on the aggregate (publication through the outbox is the deferred part already
  documented in T19-B).

`schema validation (scripts/validate-schema-mysql.ps1)`: V1→V12 applied and Hibernate `validate` passed
on MySQL 8.0.46.

## Asunciones abiertas

- **A1 — no historical bindings are invented.** The backfill of existing devices is deliberately out of
  scope; T07-A only models periods from now on.
- **A2 — channel is a free `String`** (currently only `tank-level` is used); promoting it to an enum is a
  later, cheap change.
- **A3 — publication of the binding events is deferred** with the rest of the outbox dispatcher (T19-B),
  so consumers appear in T08.
