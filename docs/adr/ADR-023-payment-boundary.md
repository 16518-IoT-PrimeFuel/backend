# ADR-023 — Payment como `legacybilling`

Estado: aceptado para S23, 2026-09-25.

Payment conserva sus tablas, IDs y endpoints v1 para no romper consumidores ni
históricos. Su command service ya no importa ni muta `FuelOrderRepository`:
registrar/completar/reembolsar un pago cambia únicamente el agregado financiero.

El estado físico de una entrega pertenece a fulfillment y puede completarse sin
un pago. La correlación `payment.orderId` permanece como referencia de lectura,
pero no es una autorización para cambiar el lifecycle de la orden.

Fuera de alcance: pasarela externa, migración destructiva o eliminación de
`payments`. Esas acciones requieren un ADR comercial posterior, conciliación,
backup/restore probado y un ledger de consumidores sin `UNKNOWN`.

## Gates

- Reintento de completar/refundar conserva la respuesta idempotente del agregado.
- Consultas v1 siguen disponibles y tenant-safe.
- Delivery no depende del servicio de Payment.
- Cualquier reemplazo financiero se introduce detrás de una interfaz nueva y
  mantiene los IDs legacy.
