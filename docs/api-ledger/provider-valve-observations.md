# Estado lógico de válvula visible al proveedor

Todas las rutas requieren JWT con ROLE_PROVIDER e identidad de proveedor. El proveedor se obtiene del principal autenticado. No se acepta organizationId ni providerId para ampliar acceso. Las listas vacías son `[]`. Los identificadores deben ser positivos. Los ejemplos son ilustrativos; los IDs provienen de respuestas reales de la API.

## Ruta

`GET /api/deliveries/{deliveryId}/valve-observations`

## Respuesta de ejemplo

```json
[
  {
    "id": 41,
    "state": "OPEN",
    "unauthorized": true,
    "commandId": null,
    "recordedAt": "2026-10-02T13:00:00Z"
  }
]
```

## Autorización y errores

403 sin proveedor autenticado. 404 entrega ajena/inexistente. Diario limitado al deliveryId y proveedor propio. POST de observaciones sigue restringido al conductor asignado; no se amplían permisos de escritura.

## Semántica

Lista ascendente del diario inmutable; último elemento es el último estado lógico registrado. state es OPEN/CLOSED. Una apertura espontánea registrada como SAFETY_INCIDENT se presenta OPEN con unauthorized=true. recordedAt es el instante de publicación del evento; no debe etiquetarse como capturedAt de hardware. commandId puede ser null. [] significa sin observaciones, no CLOSED. No emite comandos de válvula ni afirma conocer el estado físico.

## Trazabilidad y límites

US-53: hace visibles resultados lógicos e incidentes del mecanismo existente de autorización; no modifica geocercas ni reglas de descarga. Actuación física, sensores de válvula y alerta visible en frontend no se validan con esta lectura. US-52: no reemplaza tracking GPS de cisterna.
