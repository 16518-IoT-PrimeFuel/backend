# T10-A — Agregado y comandos de revisión (S10)

Parent spec: S10. Preconditions: T05-B, T06-B, T11-B, T19-B.

## Cambios del módulo `replenishment`

- Aggregate `ReplenishmentRequest` with a single lifecycle: `PENDING → ACCEPTED | REJECTED |
  CANCELLED`. All transitions are terminal and only legal from `PENDING`; `reject` requires a reason.
- **Explicit snapshots**: the request stores the `unit` (normalised through `shared` `Unit`) and the
  `unitPrice` copied from `supply.api.SupplyCatalog` at creation time, so later catalog mutations do
  not rewrite history.
- **`consumeAcceptance()` is once-only**: consuming an accepted request succeeds exactly once and
  returns `false` afterwards; consuming a non-accepted request is an error.
- Commands create/accept/reject/cancel/consume; queries by id and by organization.
- Optimistic locking via `@Version` on the persistence entity and `saveAndFlush` inside the command
  service, so a concurrent accept/reject is a conflict and exactly one decision wins.
- **Idempotency by `episodeKey`**: creating with an episode key that already exists returns the
  existing request (unique index), which is the hook S09/T09-B needs.
- `replenishment.api.ReplenishmentLookup` read seam and v2 REST
  `/api/v2/replenishment-requests` (`POST`, `GET`, `GET /{id}`, `POST /{id}/accept|reject|cancel`).
  Client-scoped reads/writes use the organization from the principal; accept/reject require the
  request's provider.
- El servicio de creación valida mediante `CustomerDirectory` que el cliente pertenezca a la organización del
  comando. Si se indica una cisterna, valida mediante `TankAssets` que exista y pertenezca a ese cliente y
  organización. Los recursos inexistentes o ajenos responden 404 para no revelar información de otro tenant.
- La dirección predeterminada del sitio se resuelve solo después de comprobar la pertenencia del cliente y la
  cisterna. El mismo servicio valida `deliveryDate` contra el día de negocio de `America/Lima` usando el `Clock`
  inyectado; los flujos automáticos omiten la fecha para que el servicio aplique la fecha de negocio vigente.
- Persistence `replenishment_requests` (`V11__replenishment_requests.sql`, validated on MySQL 8.0.46).

## Tests

`ReplenishmentRequestTest`: create snapshots the price; accept is terminal (reject afterwards fails);
acceptance consumes once then reports `false`; reject and cancel paths; unknown product rejected;
`episodeKey` creation is idempotent; and a **two-thread accept-vs-reject race has exactly one winner**.

## Decisiones vigentes

- **A1 — la aceptación v2 crea y vincula la orden.** El composition root invoca el port de ordering dentro de
  la misma transacción que acepta y consume la solicitud; el controlador de replenishment no depende de ordering.
- **A2 — cancel is allowed only while `PENDING`** (the roadmap's state matrix; cancelling an accepted
  request is not modelled).
- **A3 — the module is not yet in the ArchUnit `BUSINESS_MODULES` list**; its cross-module reads go
  through `supply.api` / `iam.api` regardless. Add it to the list when the module list is next revised.
