# Entregas del distribuidor

Todas las rutas requieren JWT con ROLE_PROVIDER e identidad de proveedor. El proveedor se obtiene del principal autenticado. No se acepta organizationId ni providerId para ampliar acceso. Las listas vacías son `[]`. Los identificadores deben ser positivos. Los ejemplos son ilustrativos; los IDs provienen de respuestas reales de la API.

## Ruta

`GET /api/deliveries?providerId=2&date=2026-10-02`

## Respuesta de ejemplo

```json
[
  {
    "id": 58,
    "orderId": 91,
    "status": "DISPATCHED",
    "physicalState": "ASSIGNED",
    "driver": {
      "id": 8,
      "firstName": "Ana",
      "lastName": "Díaz"
    },
    "tanker": {
      "id": 12,
      "licensePlate": "ABC-123"
    },
    "scheduledDate": "2026-10-02",
    "windowStart": "2026-10-02T13:00:00Z",
    "windowEnd": "2026-10-02T15:00:00Z",
    "buyerCompanyId": 4,
    "buyerCompanyName": "Comprador SAC",
    "customerAccountId": 7,
    "siteId": 10,
    "deliveryAddress": "Planta Lima",
    "requestedVolume": 800.0,
    "unit": "LITRE",
    "deliveredVolume": null
  }
]
```

## Autorización y errores

403 si falta rol/identidad o providerId no coincide con el principal. Sin providerId se usa el autenticado. Nunca consulta la colección de otros proveedores. 400 para id/fecha inválidos.

## Semántica

date es una fecha ISO y filtra scheduledDate exactamente. Sin date devuelve todas las entregas propias, por id ascendente. driver/tanker solo se enriquecen si pertenecen al proveedor. La ventana sale de la reserva persistida por assignmentCommandId, aunque ya esté liberada. Cliente/sitio salen de la orden/solicitud correlacionada. requestedVolume usa evidencia de entrega, luego solicitud, luego orden; unit usa solicitud o producto. Datos heredados ausentes devuelven null; no se inventan volúmenes ni ventanas.

## Trazabilidad y límites

Cap. 5 §5.4: datos para Entregas de hoy y lista con conductor/cisterna/ventana. US-52: esta colección no incluye GPS ni telemetría de cisterna. La UI, los botones del wireflow y el refresco siguen siendo responsabilidad del frontend.
