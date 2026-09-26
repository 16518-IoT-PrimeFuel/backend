# T21-B — Timeline reconstruida de una entrega

## Endpoint

`GET /api/v2/deliveries/{deliveryId}/timeline` está disponible al proveedor dueño o al conductor asignado. Combina `delivery_state_transitions`, decisiones y hechos del journal T21-A, y los hitos `LOAD` aún retenidos en `DeliveryTrackingQuery.samples`. La respuesta se ordena por `occurredAt`, luego `type`, luego `refId` y contiene `{ occurredAt, type, summary, refId }`.

Si falta la transición inicial `ASSIGNED`, se agrega `LEGACY_GAP`. No se crea una tabla de proyección. Después de una eliminación de evidencia GPS, desaparecen los hitos `LOAD`, mientras la historia comercial y de seguridad permanece.

## Verificación

`DeliveryTimelineControllerTest` cubre la ruta completa con decisión autorizada y comando, el gap legacy, acceso de tenant ajeno y reconstrucción después de borrar GPS.
