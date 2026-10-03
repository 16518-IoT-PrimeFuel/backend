# Asociación de tanque y dispositivo

Todas las rutas requieren JWT con ROLE_PROVIDER e identidad de proveedor. El proveedor se obtiene del principal autenticado. No se acepta organizationId ni providerId para ampliar acceso. Las listas vacías son `[]`. Los identificadores deben ser positivos. Los ejemplos son ilustrativos; los IDs provienen de respuestas reales de la API.

## Ruta

`POST /api/provider/tanks`

## Cuerpo de ejemplo

```json
{
  "buyerCompanyId": 4,
  "customerAccountId": 7,
  "siteId": 10,
  "name": "Tanque diésel",
  "fuelProductId": 5,
  "capacity": 1000,
  "unit": "LITRE",
  "initialLevel": 150,
  "lowLevelPercent": 20,
  "deviceId": "sensor-23",
  "channel": "level",
  "autoGenerateEnabled": true
}
```

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

## Autorización y errores

201 al crear. 403 sin proveedor. 404 comprador no vinculado o producto ajeno/inactivo. Cuenta/sitio deben pertenecer al comprador verificado (400 si no). 409 si el dispositivo ya está vinculado a otro tanque, incluso en otro canal; solo expone el tankId existente si también está autorizado. Producto debe pertenecer al proveedor. El cuerpo no acepta organizationId/providerId para determinar autorización.

## Semántica

Creación atómica de tanque + configuración + vínculo de dispositivo + política. lowLevelPercent debe ser >0 y <=90 (histéresis inicial 10 puntos). unit es LITRE o GALLON; nivel inicial opcional, por defecto 0, <= capacidad. deviceId máximo 120 y channel máximo 60 caracteres. autoGenerateEnabled por defecto true: permite activar la generación existente al recibir lecturas autenticadas. La asociación no emite credenciales; el hardware requiere la credencial técnica X-Device-Token previamente provisionada. La ingesta existente resuelve el tanque por deviceId/channel/capturedAt y conserva deduplicación/cuarentena. El comprador conserva /api/tanks como contingencia; no se amplían sus permisos.

## Trazabilidad y límites

US-51 esc. 1: asociación y umbral persistidos; esc. 2: deviceId asociado a otro tanque se rechaza, incluso con otro canal. Identidad técnica del modelo: dispositivo/canal, no una columna duplicada en Tank. Cap. 5: datos listos para formulario de asociación. Provisionamiento físico/token, guard del frontend y prueba con hardware permanecen fuera del alcance.
