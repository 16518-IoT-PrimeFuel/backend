# T07-B — Provisionamiento y revocación técnica (S07)

Parent spec: S07. Precondition: T07-A.

## What was added (equipment, package `equipment.devicebinding`)

- Aggregate `DeviceCredential` (device, channel, `token_hash`, `token_version`, status
  `ACTIVE|REVOKED`) and `DeviceTokenHasher`: the token is 32 random bytes (URL-safe Base64) and only its
  **SHA-256 hex** is persisted. The raw token leaves the process exactly once, at provision/rotate time,
  and is never logged or attached to an event.
- `DeviceCredentialService`: `provision` (refused when the channel already has an active credential —
  you must rotate), `rotate` (revokes the current credential and issues the next version), `revoke`.
- Public machine seam `equipment.api.DeviceAuthentication.authenticate(deviceId, channel, token, instant)`
  returning a `Decision(outcome, tankId, organizationId)`. Outcomes:
  `AUTHENTICATED`, `UNKNOWN_CREDENTIAL`, `REVOKED_CREDENTIAL`, `NO_ACTIVE_BINDING`. Only `AUTHENTICATED`
  carries a resolved tank; every other outcome means the caller must **quarantine** the reading.
- Credential verification is a constant-time comparison, and the device/channel in the token record must
  match the claimed ones.
- Persistence `device_credentials` (`V13__device_credentials.sql`, unique `token_hash`) validated with
  Flyway + Hibernate `validate` on MySQL 8.0.46 (the first run failed with *missing column
  `updated_at`* — the audit column of the shared base entity — and was fixed).

## Tests

`DeviceProvisioningTest`:
- province returns a raw token that is **not** stored (lookup by raw token finds nothing, lookup by hash
  finds the record);
- valid credential with no binding → `NO_ACTIVE_BINDING` (quarantine);
- forged token → `UNKNOWN_CREDENTIAL`;
- second provision on the same channel is refused;
- after a bind, the token authenticates and resolves tank **and tenant**;
- **rotation** invalidates the previous token immediately (`REVOKED_CREDENTIAL`) and the new token works;
- **boundary**: after a move, `T1` resolves to the new tank and `T1-1s` to the old one;
- **revocation** stops authentication, and revoking twice is refused;
- overlap is still rejected on the move path.

## Asunciones abiertas

- **A1 (technical decision, not business) — transport and credential format.** S07 explicitly excludes
  "firmware y protocolo definitivo" from scope, so the simplest standard was chosen: HTTPS + opaque
  rotating bearer token stored hashed. No mTLS, no HMAC signing, no certificate pinning yet.
- **A2 — credential administration is exposed as internal commands**, not as a REST surface: the tenant
  mapping needed to authorize such calls safely lands with the telemetry work (T08-A). No half-guarded
  endpoint was added on purpose.
- **A3 — rotation is operator/ops-driven** (no automatic schedule). An automated rotation policy is a
  follow-up once ingestion volume is known.
