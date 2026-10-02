# Episodios de reposición

Todas las rutas requieren JWT con ROLE_PROVIDER e identidad de proveedor. El proveedor se obtiene del principal autenticado. No se acepta organizationId ni providerId para ampliar acceso. Las listas vacías son `[]`. Los identificadores deben ser positivos. Los ejemplos son ilustrativos; los IDs provienen de respuestas reales de la API.

## Ruta

`GET /api/provider/tanks/{tankId}/refill-episodes`

## Respuesta de ejemplo

```json
[
  {
    "id": 31,
    "episodeKey": "tank-23-policy-1-episode-1",
    "tankId": 23,
    "organizationId": 6,
    "policyVersion": 1,
    "status": "OPEN",
    "openedAt": "2026-10-02T12:00:00Z",
    "openedLevelPercent": 15.0,
    "openedLevel": 150.0,
    "targetLevel": 1000.0,
    "requestedVolume": 850.0,
    "unit": "LITRE",
    "requestEmitted": true,
    "requestId": 87,
    "closedAt": null,
    "closedLevelPercent": null,
    "version": 0
  }
]
```

## Autorización y errores

403 sin rol/identidad de proveedor. 404 tanque inexistente o dueño sin relación comercial con el proveedor. Se valida el propietario del tanque y se filtran episodios por su organización. La ruta /api/tanks/{tankId}/refill-episodes mantiene la autorización del dueño.

## Semántica

Mismo resource y assembler que la ruta existente. No hay creación ni evaluación de episodios en esta consulta. [] si no hay episodios. Cantidad, apertura y requestId permiten enlazar con solicitudes; no se alteran las políticas.

## Trazabilidad y límites

US-32: historia de episodios/volúmenes/fechas de reposición. No reemplaza el detalle comercial de órdenes, solicitudes y entregas. UI de historial vacío permanece en frontend.
