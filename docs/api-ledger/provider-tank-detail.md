# Detalle de tanque del distribuidor

`GET /api/provider/tanks/{tankId}` devuelve exactamente `ProviderTankResource`, el mismo contrato del listado, sin recorrer todos los tanques. `tankId` debe ser positivo.

```json
{"id":23,"buyerCompanyId":4,"organizationId":6,"customerAccountId":7,"siteId":10,"name":"Tanque diésel","siteName":"Planta","deliveryAddress":"Lima","fuelType":"DIESEL","fuelProductId":5,"capacity":1000.0,"currentLevel":150.0,"unit":"LITRE","levelPercent":15.0,"lowLevelPercent":20.0,"critical":true,"levelObservedAt":null,"levelSource":"MANUAL","devices":[]}
```

Solo ROLE_PROVIDER con identidad IAM. ProviderBuyerAccess comprueba vínculo explícito, órdenes o solicitudes; el proveedor viene de TenantAccess, nunca de la URL. 404 TANK_NOT_FOUND tanto para inexistente como para inactivo o ajeno; 400 VALIDATION_ERROR para id inválido, 403 FORBIDDEN sin rol/identidad. Todos usan el recurso de error compartido. No hay migración.

Trazabilidad: [chapter3 develop](https://github.com/16518-IoT-PrimeFuel/Report/blob/develop/docs/chapter3.md), US-32 y US-51; [chapter5 §5.4](https://github.com/16518-IoT-PrimeFuel/Report/blob/develop/docs/chapter5.md), Clientes y tanques. Este detalle aporta nivel, dispositivo y política; no cubre por sí solo historial de pedidos/cantidades/fechas de US-32, cambios de umbral/asociación IoT de US-51, ni renderizado/estados vacíos de la UI. Esas operaciones existentes no se modifican.

Verificación pendiente: ProviderTankDetailTest incluye lectura, otro proveedor, rol comprador, id inválido e inexistente. No se compiló ni ejecutó Maven por instrucción del usuario; Claude revisará y ejecutará las pruebas. El snapshot se actualizó contractualmente; debe contrastarse con /api-docs al verificar los tests.
