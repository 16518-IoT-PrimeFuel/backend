# PrimeFuel migration roadmap — checklist

Sigue `docs/PRIMEFUEL_MIGRATION_ROADMAP.md`. Se marca cada ticket cuando su build queda verde y
sus entregables están confirmados (no implica commit — ver estado de cada uno).

## W0 — Baseline reproducible (S01)

- [x] **T01-A** — Baseline de rutas y contratos. Ledger 77/77 + 11 tests golden. Build 21/21 verde.
      → `docs/api-ledger/T01-A-rest-ledger.md`
- [x] **T01-B** — Caracterización de estados y seguridad. 7 known-gaps reproducidos con test
      determinista (2 luego resueltos por hotfix, ver abajo). Build 30/30 verde.
      → `docs/api-ledger/T01-B-state-security-characterization.md`

## Hotfixes fuera de secuencia

- [x] **Hotfix R01 (2026-09-21)** — Fuga cross-tenant CRITICAL en `POST /api/v1/fuel-orders`:
      sin validar que `providerId` sea dueño de `fuelProductId`, ni que `equipmentId` pertenezca
      a `companyId` (permitía mutar el tanque de otro tenant). Corregido en
      `FuelOrderCommandServiceImpl`. Build 30/30 verde.
      → detalle en `docs/api-ledger/T01-B-state-security-characterization.md` (gaps #6 y #7)

## W1 — Guardrails, esquema y entrega durable (S02, S03, S19)

- [ ] T02-A — Spike y reglas de dependencia de módulos
- [ ] T02-B — Interfaz pública piloto y baseline de arquitectura
- [ ] T03-A — Inventario de esquema y baseline
- [ ] T03-B — Sustitución controlada del DDL de arranque
- [ ] T19-A — Registro de publicación y contratos de eventos
- [ ] T19-B — Idempotencia, replay y observabilidad

## W2 — Identidad, tenant y clientes (S04, S05)

- [ ] T04-A — Modelo y resolución de membresía (Organization/Membership)
- [ ] T04-B — Onboarding, invitación y compatibilidad IAM
- [ ] T05-A — CustomerAccount y sitios
- [ ] T05-B — Mapa y backfill de pertenencia

## W3 — Supply, tanques y solicitud manual (S11, S06, S10)

- [ ] T11-A — Interfaz Supply y unidades
- [ ] T11-B — Reserva y conciliación de suministro
- [ ] T06-A — Modelo Tank e interfaz de activos
- [ ] T06-B — Mapeo legacy y snapshot de nivel
- [ ] T10-A — Agregado y comandos de revisión (ReplenishmentRequest)
- [ ] T10-B — Puente FuelRequest/FuelOrder compatible

## W4 — Dispositivos, telemetría y reposición automática (S07, S08, S09)

- [ ] T07-A — Modelo temporal de DeviceBinding
- [ ] T07-B — Provisionamiento y revocación técnica
- [ ] T08-A — Adapter y almacenamiento normalizado de telemetría
- [ ] T08-B — Integración idempotente con Tank
- [ ] T09-A — Regla de reposición y episodios
- [ ] T09-B — Generación automática idempotente

## W5 — Fleet, reservas, delivery y asignación (S12, S13, S14, S15)

- [ ] T12-A — Extraer Fleet de CRUD
- [ ] T12-B — Política y consulta de elegibilidad
- [ ] T13-A — Modelo y cálculo de capacidad
- [ ] T13-B — Reserva concurrente y liberación
- [ ] T14-A — Lifecycle y comandos de ejecución de Delivery
- [ ] T14-B — Compatibilidad de estados y cierre físico
- [ ] T15-A — Interfaz de asignación transaccional
- [ ] T15-B — Eliminar accesos cruzados y probar carreras

## W6 — Tracking, safety y válvula (S16, S17, S18, S21)

- [ ] T16-A — Contrato de telemetría de transporte
- [ ] T16-B — Proyección y consulta de seguimiento
- [ ] T17-A — Modelo y validación de geocerca
- [ ] T17-B — Decisión safety y evidencia versionada
- [ ] T18-A — Protocolo y outbox de comandos de válvula
- [ ] T18-B — ACK, incidentes y prueba de hardware (requiere banco físico)
- [ ] T21-A — Journal transaccional de negocio
- [ ] T21-B — Timeline y proyección reconstruible

## W7 — Notificaciones, contratos, billing y retiro (S20, S22, S23, S24)

- [ ] T20-A — Destinatarios y suscriptores
- [ ] T20-B — Bandeja compatible y reintentos
- [ ] T22-A — Auditoría de consumidores y contratos v2
- [ ] T22-B — Adapters y plan de sunset verificado
- [ ] T23-A — Decisión documentada de cobros (ADR)
- [ ] T23-B — Aislamiento de Payment y estado físico
- [ ] T24-A — Limpieza de marcadores y docs
- [ ] T24-B — Retiro controlado de endpoints confirmados
