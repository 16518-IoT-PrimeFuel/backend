# Recomendación de recursos

Todas las rutas requieren JWT con ROLE_PROVIDER e identidad de proveedor. El proveedor se obtiene del principal autenticado. No se acepta organizationId ni providerId para ampliar acceso. Las listas vacías son `[]`. Los identificadores deben ser positivos. Los ejemplos son ilustrativos; los IDs provienen de respuestas reales de la API.

## Ruta

`GET /api/deliveries/recommendation?orderId=91`

## Respuesta de ejemplo

```json
{
  "orderId": 91,
  "recommended": true,
  "reason": null,
  "driverId": 8,
  "driverName": "Ana Díaz",
  "tankerId": 12,
  "licensePlate": "ABC-123",
  "tankerCapacityLitres": 1000.0,
  "requestedVolumeLitres": 800.0,
  "windowStart": "2026-10-02T00:00:00Z",
  "windowEnd": "2026-10-03T00:00:00Z",
  "criterion": "Smallest sufficient tanker in litres, then lowest tanker id; lowest eligible driver id; no active reservation overlap"
}
```

## Autorización y errores

403 sin proveedor. 404 orden ajena/inexistente o sin solicitud propia correlacionada. 409 solicitud no aceptada o cuya aceptación ya se consumió. 400 id/ventana inválida. No crea entregas ni reservas; POST /api/deliveries conserva la asignación transaccional y revalida disponibilidad.

## Semántica

Usa EligibilityQuery (misma lógica de /drivers/eligible y /tankers/eligible), capacidad convertida a litros y reservas activas superpuestas. Prefiere la cisterna de menor capacidad suficiente, empates por menor id, y el conductor de menor id sin conflicto. Admite windowStart/windowEnd ISO UTC juntos; sin ellos considera el día programado completo UTC. Si no hay fecha, hay que enviar la ventana. Sin candidatos: 200 con recommended=false, IDs/nombres/capacidad null y reason NO_ELIGIBLE_DRIVER, NO_SUFFICIENT_ELIGIBLE_TANKER o RESERVATION_CONFLICT. Esta propuesta no garantiza disponibilidad futura; la asignación definitiva toma los locks existentes.

## Trazabilidad y límites

US-49: selección, capacidad y conflictos de horario. El modelo actual no describe compatibilidad de cisterna por producto ni un costo de ruta; no se inventan. Asignación final/estado Asignado sigue en POST /api/deliveries. Cap. 5: frontend puede preseleccionar y mostrar resumen, confirmación y motivos de falta de recursos.
