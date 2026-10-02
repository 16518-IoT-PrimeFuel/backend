# Analítica del distribuidor

Todas las rutas requieren JWT con ROLE_PROVIDER e identidad de proveedor. El proveedor se obtiene del principal autenticado. No se acepta organizationId ni providerId para ampliar acceso. Las listas vacías son `[]`. Los identificadores deben ser positivos. Los ejemplos son ilustrativos; los IDs provienen de respuestas reales de la API.

## Ruta

`GET /api/analytics/providers/{providerId}?from=2026-10-01&to=2026-10-31`

## Respuesta de ejemplo

```json
{
  "providerId": 2,
  "totalOrders": 12,
  "confirmedOrders": 4,
  "cancelledOrders": 1,
  "totalRevenue": 1500.0,
  "monthlyRevenue": [
    {
      "month": "2026-10",
      "monthIndex": 10,
      "amount": 1500.0
    }
  ],
  "pendingOrders": 3,
  "totalFuelSoldLitres": 800.0,
  "salesTrend": [
    {
      "date": "2026-10-02",
      "litres": 800.0
    }
  ]
}
```

## Autorización y errores

403 para otro proveedor o usuario sin rol/identidad propia. from/to son fechas ISO, opcionales de forma independiente e inclusivas; 400 si son inválidas o from > to.

## Semántica

Sin fechas conserva totalOrders, confirmedOrders, cancelledOrders, totalRevenue y monthlyRevenue acumulados. Pedidos se filtran por createdAt convertido a UTC; ingresos por paidAt tal como se persiste (LocalDateTime). confirmedOrders conserva la definición previa CONFIRMED o DELIVERED; pendingOrders cuenta PENDING. Venta en litros: cantidad comercial de cada orden con un pago COMPLETED, una sola vez, en la fecha del primer pago completado; excluye órdenes CANCELLED y unidades sin fuente conocida. Usa la unidad/cantidad de la solicitud correlacionada o la unidad del producto, convirtiendo galones a litros. salesTrend es una serie diaria dispersa y ordenada: el frontend suma por semana/mes y completa días vacíos con cero. No equivale a volumen físico descargado; ingresos sí suman todos los pagos completados del período.

## Trazabilidad y límites

US-47 esc. 1: KPIs y serie filtrable para el gráfico; vistas diaria/semanal/mensual se agrupan en frontend. Esc. 2: navegación de UI pendiente. US-48: no se implementa distribución industrial ni exportación PDF. Panel del cap. 5: tanques críticos y entregas del día se obtienen de las rutas dedicadas, solicitudes pendientes del endpoint ya existente.
