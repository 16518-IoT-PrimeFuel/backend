# Edición del tanque vinculado


`PUT /api/provider/tanks/{tankId}`

```json
{
  "fuelProductId": 5,
  "lowLevelPercent": 25,
  "deviceId": "sensor-replacement",
  "channel": "level"
}
```

200 devuelve el mismo contrato de tanque. Campos omitidos se conservan; deviceId y channel deben enviarse juntos. Cambiar dispositivo cierra los vínculos abiertos de ese canal y conserva la historia para lecturas atrasadas. No mueve un dispositivo ocupado en otro tanque. No cambia propietario, sitio ni capacidad. Nuevo producto debe ser activo y propio; cambiar su tipo versiona la configuración. Sin nuevo producto conserva el proveedor/producto de la política previa. Un tanque sin política puede editar umbral/dispositivo sin producto; activar generación exige producto. 404 tanque no vinculado; 409 dispositivo duplicado; 400 datos inválidos. Los cambios completos se revierten si falla cualquiera de sus pasos.

## Respuesta de ejemplo

```json
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
  "levelSource": "MANUAL",
  "devices": [
    {
      "deviceId": "sensor-23",
      "channel": "level",
      "validFrom": "2026-10-02T12:00:00Z"
    }
  ]
}
```


US-51: edición de umbral/producto/dispositivo; el formulario de cap. 5 y la provisión física siguen pendientes. Requiere ROLE_PROVIDER y comprador vinculado mediante vínculo explícito, pedidos o solicitudes.
