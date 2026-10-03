# Tanques vinculados: lectura

Todas las rutas requieren JWT con ROLE_PROVIDER e identidad de proveedor. El proveedor se obtiene del principal autenticado. No se acepta organizationId ni providerId para ampliar acceso. Las listas vacías son `[]`. Los identificadores deben ser positivos. Los ejemplos son ilustrativos; los IDs provienen de respuestas reales de la API.

## Ruta

`GET /api/provider/tanks?buyerCompanyId=4`

## Respuesta de ejemplo

```json
[
  {
    "id": 23,
    "buyerCompanyId": 4,
    "organizationId": 6,
    "customerAccountId": 7,
    "siteId": 10,
    "name": "Tanque diésel",
    "siteName": "Planta Lima",
    "deliveryAddress": "Planta Lima",
    "fuelType": "DIESEL",
    "fuelProductId": 5,
    "capacity": 1000.0,
    "currentLevel": 150.0,
    "unit": "LITRE",
    "levelPercent": 15.0,
    "lowLevelPercent": 20.0,
    "critical": true,
    "levelObservedAt": "2026-10-02T12:00:00Z",
    "levelSource": "VALIDATED",
    "devices": [
      {
        "deviceId": "sensor-23",
        "channel": "level",
        "validFrom": "2026-10-01T00:00:00Z"
      }
    ]
  }
]
```

## Autorización y errores

403 sin proveedor autenticado. 404 si el filtro apunta a comprador no vinculado/inexistente. Sin filtro devuelve tanques de compradores con vínculo explícito, órdenes o solicitudes propias. Solo organizaciones verificadas por pedidos, o atribuidas por solicitudes reales. Sitios y dispositivos se validan contra la organización del tanque.

## Semántica

Lista tanques activos, por id ascendente. Nivel/capacidad se expresan en unit; levelPercent y lowLevelPercent son porcentajes. critical usa <=. fuelType siempre está disponible; fuelProductId solo si la política referencia un producto de este proveedor. devices contiene vínculos abiertos y vigentes por instante, sin tokens ni hashes. levelObservedAt/levelSource permiten distinguir lectura IoT y edición manual. devices puede estar vacío. No requiere migración: V12 ya persiste device_bindings(tank_id, device_id, channel) y V13 las credenciales; no se duplican estos vínculos en tanks.

## Trazabilidad y límites

US-31 y US-51: asociación IoT, producto y umbral visibles. Cap. 5 §5.4: datos para barra de nivel y política. La UI y la visualización de rangos quedan fuera del backend. La escritura se documenta en provider-tank-management.md.
