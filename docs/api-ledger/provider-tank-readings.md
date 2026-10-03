# Lecturas IoT visibles al proveedor

Todas las rutas requieren JWT con ROLE_PROVIDER e identidad de proveedor. El proveedor se obtiene del principal autenticado. No se acepta organizationId ni providerId para ampliar acceso. Las listas vacías son `[]`. Los identificadores deben ser positivos. Los ejemplos son ilustrativos; los IDs provienen de respuestas reales de la API.

## Ruta

`GET /api/provider/tanks/{tankId}/readings?from=2026-10-01T00:00:00Z&to=2026-10-03T00:00:00Z`

## Respuesta de ejemplo

```json
[
  {
    "id": 71,
    "tankId": 23,
    "deviceId": "sensor-23",
    "channel": "level",
    "sequence": 100,
    "level": 150.0,
    "unit": "LITRE",
    "capturedAt": "2026-10-02T12:00:00Z",
    "receivedAt": "2026-10-02T12:00:05Z",
    "quality": "ACCEPTED"
  }
]
```

## Autorización y errores

403 sin proveedor autenticado. 404 tanque no vinculado/inexistente. 400 id/timestamp/rango inválido. Se consulta tankId+organizationId atribuidos durante ingesta; excluye QUARANTINED y no reasigna historia por vínculos actuales.

## Semántica

from/to son instantes ISO con zona (recomendado Z), opcionales e inclusivos, filtran capturedAt. Orden ascendente por capturedAt/id. [] sin lecturas. receivedAt mide recepción; capturedAt permite calcular antigüedad y retrasos. Mantiene lecturas de sensores reemplazados si originalmente fueron atribuidas a este tanque. No consulta credenciales ni expone hashes.

## Trazabilidad y límites

US-52: datos históricos y antigüedad del nivel de tanque; ingesta existente conserva secuencias y cuarentena. Esta ruta no aporta ubicación/estado de cisterna asociados a una entrega, ni comunicación en tiempo real, ni prueba de sincronización del hardware. Cap. 5: UI debe mostrar última lectura/antigüedad.
