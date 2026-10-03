# Compradores vinculados

Todas las rutas requieren JWT con ROLE_PROVIDER e identidad de proveedor. El proveedor se obtiene del principal autenticado. No se acepta organizationId ni providerId para ampliar acceso. Las listas vacías son `[]`. Los identificadores deben ser positivos. Los ejemplos son ilustrativos; los IDs provienen de respuestas reales de la API.

## Ruta

`GET /api/provider/buyer-companies`

## Respuesta de ejemplo

```json
[
  {
    "id": 4,
    "name": "Comprador SAC",
    "organizationId": 6,
    "tankCount": 2,
    "criticalTankCount": 1,
    "activeOrderCount": 1,
    "historicalOrderCount": 5,
    "sites": [
      {
        "id": 10,
        "customerAccountId": 7,
        "name": "Planta Lima",
        "address": "Planta Lima"
      }
    ]
  }
]
```

## Autorización y errores

El resultado se limita a compradores con vínculo explícito, órdenes o solicitudes del proveedor autenticado. Otro proveedor obtiene solo sus propios compradores, o []. Rol incorrecto: 403. Los vínculos explícitos de V38 permiten compradores sin historial ni usuario IAM. Para relaciones históricas de órdenes/solicitudes, el mapeo comprador-organización conserva RUC coincidente y membresía activa, reutilizando LegacyCompanyDirectory.

## Semántica

id es BuyerCompany.id y se usa como buyerCompanyId. organizationId identifica la organización compradora, nunca el proveedor. Las empresas deduplican ambas fuentes de relación. Los tanques se cuentan activos; críticos cuando nivel porcentual <= umbral de política o 20% por defecto. activeOrderCount incluye PENDING, CONFIRMED, DISPATCHED, IN_PROGRESS, PENDING_PAYMENT; historicalOrderCount cuenta todas las órdenes con este proveedor. sites permite obtener customerAccountId/siteId para POST /api/provider/tanks. Si una compañía histórica no tiene mapeo verificado a organización, conserva nombre e historial comercial, con organización null, sin tanques/sitios.

## Trazabilidad y límites

US-31 esc. 1: nombre, pedidos activos, total histórico y tanques. Esc. 2: [] habilita el mensaje vacío en UI. Cap. 5 Clientes y tanques: sitios incluidos. US-32: las cantidades/fechas detalladas de órdenes se consultan por las rutas existentes de pedidos/solicitudes; esta ruta no entrega un historial completo. Solicitudes sin identidad BuyerCompany verificable no se reinterpretan como otro ID.
