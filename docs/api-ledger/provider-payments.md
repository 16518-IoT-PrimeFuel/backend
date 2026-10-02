# Pagos del distribuidor

`GET /api/payments/provider/{providerId}?status=PENDING&from=2026-10-01T00:00:00Z&to=2026-10-31T23:59:59Z`

```json
[{"id":9,"orderId":30,"buyerCompanyId":4,"buyerName":"Comprador SAC","amount":200.0,"currency":null,"status":"PENDING","paymentMethod":"BANK_TRANSFER","createdAt":"2026-10-02T12:00:00Z","updatedAt":"2026-10-02T12:00:00Z","paidAt":null}]
```

Solo ROLE_PROVIDER propietario, mediante @currentUserAccess.ownsProvider(providerId); la consulta usa currentProviderId de TenantAccess. El id de la URL solo comprueba identidad y no selecciona otro tenant. No se amplía comprador/admin. 403 FORBIDDEN para otro proveedor o rol, 400 VALIDATION_ERROR para ids no positivos, estados/fechas inválidos o from > to.

Filtros opcionales: status = PENDING, COMPLETED, FAILED, REFUNDED; from/to son instantes ISO con zona y límites inclusivos sobre createdAt (no paidAt). Orden createdAt DESC, id DESC. Sin resultados: []. paidAt conserva el LocalDateTime sin zona del modelo existente; no debe interpretarse como UTC. createdAt/updatedAt usan el instante de auditoría. currency es null porque ni payments ni fuel_orders persistían moneda: el frontend debe mostrar moneda no informada y no sumar importes como una divisa confirmada. No se agrega una moneda inventada ni migración en este alcance.

Una proyección SQL une payments, fuel_orders y buyer_companies, restringiendo provider_id y verificando también company_id del pago frente a la orden. Los filtros se ejecutan en SQL, sin repositorios ajenos en payment ni consultas por fila. ProviderPaymentsTest comprueba una única sentencia para la lectura, aislamiento, roles, filtros inclusivos, lista vacía y errores de parámetros.

Trazabilidad: [chapter5 §5.4 develop](https://github.com/16518-IoT-PrimeFuel/Report/blob/develop/docs/chapter5.md), pantalla Pagos (US-13); este GET aporta pagos por orden y estado. Confirmación/reembolso pertenecen a endpoints existentes y no se cambian ni se declara verificado su flujo completo. US-31/32/51 de [chapter3](https://github.com/16518-IoT-PrimeFuel/Report/blob/develop/docs/chapter3.md) no se completan mediante esta lista financiera. La moneda histórica y la validación/renderizado de la UI quedan sin cubrir.

No se compiló ni ejecutó Maven por instrucción del usuario; Claude revisará y ejecutará las pruebas. Snapshot actualizado contractualmente, pendiente de contraste con /api-docs generado.
