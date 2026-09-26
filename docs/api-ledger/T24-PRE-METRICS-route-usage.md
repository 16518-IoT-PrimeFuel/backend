# T24-PRE-METRICS — Uso de rutas por versión

## Registro

`ApiRouteMetricsInterceptor.afterCompletion` cuenta solo requests que llegaron a un `HandlerMethod` bajo
`/api/v1/**` o `/api/v2/**`. La clave es el método HTTP más `BEST_MATCHING_PATTERN_ATTRIBUTE`; nunca guarda
IDs de la URL. El handler se registra como `Controller#method`.

El caller es el ID de organización de una membresía activa si está disponible; para usuarios legacy se usa
`company:{id}` o `provider:{id}`. Requests sin principal reconocido se agrupan como `anonymous`. No se guarda
username ni PII. Un fallo de medición se registra en el log y no altera la respuesta API.

V27 crea `api_route_metrics` y `api_route_callers`. El contador se incrementa con `UPDATE` y, ante ausencia,
`INSERT`; una carrera en la inserción vuelve a incrementar la fila ganadora. La clave primaria compuesta hace
idempotente el conteo de callers distintos. No hay scheduler: `GET /api/v2/admin/api-metrics` entrega el
reporte ordenado por versión/ruta y acepta `?version=v1` o `?version=v2`; requiere `ROLE_ADMIN`.

## Verificación

`ApiRouteMetricsControllerTest` comprueba que distintos IDs de ruta producen una clave de patrón común,
cuentan solicitudes y separan callers. También comprueba el acceso admin. `ApiRouteMetricsFailureTest`
comprueba que una excepción en el recorder no cambia el status del endpoint.
