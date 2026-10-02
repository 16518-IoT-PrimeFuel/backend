# Contratos de operación del distribuidor

Fecha: 2026-10-02. Rama: feat/w2-debug. Preparado para frontend-implementer-pt2.

Fuentes verificadas: [chapter1 §1.3](https://github.com/16518-IoT-PrimeFuel/Report/blob/develop/docs/chapter1.md), [chapter3](https://github.com/16518-IoT-PrimeFuel/Report/blob/develop/docs/chapter3.md), [chapter5 §5.4](https://github.com/16518-IoT-PrimeFuel/Report/blob/develop/docs/chapter5.md) y ../frontend/plan/ROADMAP.md. Landing excluida. El distribuidor es el usuario principal.

| Método y ruta | Contrato completo |
|---|---|
| GET /api/deliveries | [Entregas](provider-deliveries.md) |
| GET /api/analytics/providers/{providerId} | [Analítica y período](provider-analytics.md) |
| GET /api/provider/buyer-companies | [Compradores y sitios](provider-buyer-companies.md) |
| GET /api/provider/buyer-companies/lookup | [RUC exacto y vínculo de comprador registrado](provider-buyer-lookup.md) |
| POST /api/provider/buyer-companies | [Alta y vínculo explícito](provider-buyer-registration.md) |
| GET /api/provider/tanks | [Tanques](provider-tanks.md) |
| GET /api/provider/tanks/{tankId} | [Detalle de tanque](provider-tank-detail.md) |
| GET /api/payments/provider/{providerId} | [Pagos del distribuidor](provider-payments.md) |
| POST /api/provider/tanks | [Asociación](provider-tank-management.md) |
| PUT /api/provider/tanks/{tankId} | [Edición](provider-tank-update.md) |
| GET /api/provider/tanks/{tankId}/refill-episodes | [Episodios](provider-refill-episodes.md) |
| GET /api/deliveries/recommendation | [Recomendación](provider-recommendation.md) |
| GET /api/provider/tanks/{tankId}/readings | [Lecturas](provider-tank-readings.md) |
| GET /api/deliveries/{deliveryId}/valve-observations | [Válvula](provider-valve-observations.md) |

## Decisiones para integración

- Antes del primer pedido: POST /api/provider/buyer-companies crea o vincula al comprador; para uno registrado, consultar primero el RUC exacto en lookup y enviar el buyerCompanyId obtenido. Usar id y sites del POST para POST /api/provider/tanks. El lookup solo revela identidad mínima y tiene cuota; el vínculo explícito, pedidos o solicitudes habilitan el acceso operativo. Ver [lookup](provider-buyer-lookup.md) y [contrato de alta](provider-buyer-registration.md).

- buyerCompanyId = id de buyer-companies. customerAccountId/siteId se obtienen de sites en esa respuesta. Los IDs de organización, cuenta y compañía son distintos.
- deviceId + channel identifica el canal IoT en el modelo existente; devices devuelve solo asociaciones vigentes, sin secretos. POST/PUT impiden asociar el mismo dispositivo a otro tanque incluso en un canal distinto. Hay V12/V13 y resolución temporal en ingesta, por lo que no hace falta una migración IoT ni cambiar POST /api/telemetry/readings. V38 agrega provider_buyer_links para admitir compradores sin pedidos previos.
- Las rutas de comprador y ADMIN conservan autorización. POST /api/tanks permanece como contingencia documentada.
- SalesTrend diario sirve para agrupar vistas diaria/semanal/mensual en UI. Los litros vendidos tienen definición comercial por pago; no son volumen descargado.
- HTTP 403 indica identidad/rol incorrecto; 404 oculta recursos de otros tenants. [] es un estado vacío válido. Campos null representan información ausente, no datos ficticios.
- Swagger publica schemas de las nuevas lecturas y de POST/PUT; los tests consultan /api-docs.

## Criterios que permanecen fuera del encargo

US-47 navegación/renderizado y actualización dinámica del panel; US-48 sector industrial/PDF; US-49 optimización de ruta y compatibilidad de cisterna por producto (no hay modelo), además de confirmación visual en frontend; US-51 provisión del token/hardware y guard/formulario del distribuidor; US-52 telemetría física/GPS de cisterna y sincronización real del dispositivo; US-53 actuación de hardware y alerta visible. Los mensajes vacíos y gráficos de US-31/32/cap. 5 requieren UI. No se afirma E2E de frontend ni equivalencia entre pruebas H2 y una ejecución de Flyway/MySQL.

## Validación

`./mvnw.cmd test`: BUILD SUCCESS el 2026-10-02. 282 tests, 0 fallos, 0 errores, 3 omitidos. H2; incluye aislamiento, alta sin historial, formato de errores, Swagger y límites de módulos. Las pruebas de cada ruta incluyen caso feliz y otro proveedor; hay pruebas adicionales de duplicado, rollback, períodos, lectura atribuida, reservas y OpenAPI.

V38 está entregada como código; no se ejecutaron migraciones contra una base MySQL real.
