# T24-A — Limpieza de marcadores y docs (S24)

Deps: T22-B, T23-B, T03-B. **Alcance: borrar solo clases vacías confirmadas y alinear docs. NO se retira
ningún endpoint** (eso es T24-B, explícitamente bloqueado).

## Verificación previa (antes de borrar nada)

Para cada candidato se comprobó:

1. **Cuerpo de la clase**: clase pública **sin** `@RestController`/`@Component`, sin `@RequestMapping` y sin
   ningún método (marcador puro).
2. **Referencias en el repo**: búsqueda de los 6 nombres en todo `src/**` y `docs/**`. En código **solo** la
   propia declaración (cero usos, cero reflection, cero registro de beans); las demás menciones eran
   documentación.
3. **Conteo de rutas**: `ApiLedgerSelfCheckTest` / `OpenApiSnapshotTest` demuestran que aportan **0** mappings
   (el baseline es 77 y no cambia).

## Eliminadas (6)

| Clase | Módulo |
|---|---|
| `FulfillmentController` | fulfillment |
| `DirectoryController` | iam |
| `InventoryController` | inventory |
| `OrderingController` | ordering |
| `PaymentController` | payment |
| `NotificationController` | notification |

## Docs regeneradas/alineadas

- `docs/api-ledger/T01-A-rest-ledger.md`: la nota de "empty placeholders" ahora registra que T24-A los eliminó
  y que el conteo de 77 rutas no cambió.
- `docs/ARCHITECTURE_REPORT.md`: la observación de clases vacías refleja la eliminación.
- `roadmap-checklist.md` (sección Swagger): los placeholders ya no se listan como excluidos vivos.
- `docs/api-ledger/T23-A-payment-characterization.md`: finding **F9** marcado resuelto.
- `docs/diagrams/*.puml` (6): se quitaron los nodos `class XController` y la relación `DirectoryController ..>`
  de `iam.puml`.
- El snapshot OpenAPI se regenera por `OpenApiSnapshotTest`; sin cambios de paths (estas clases no exponían
  ninguno).

## Garantías

- Ninguna ruta/contrato cambiado; el 77/77 sigue verde.
- No se tocó T24-B (retiro real): sigue **bloqueado** por el registro de sunset (T22-B) sin aprobar.

## Build

`./mvnw.cmd test`: verde (ver checklist). Sin cambios de esquema.
