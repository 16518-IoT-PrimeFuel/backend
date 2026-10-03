# Comprador registrado: lookup y vínculo

`GET /api/provider/buyer-companies/lookup?ruc=20123456789`

```json
{"buyerCompanyId":4,"name":"Comprador SAC","ruc":"20123456789"}
```

Solo ROLE_PROVIDER con identidad de proveedor autenticado. RUC exacto de 11 dígitos ASCII; no hay listados ni búsquedas parciales. 400 VALIDATION_ERROR para RUC ausente, vacío o inválido; 404 BUYERCOMPANY_NOT_FOUND si no existe. No devuelve sector, organización, contactos, sitios, pedidos ni tanques. Una respuesta 200 no crea vínculo ni habilita lecturas operativas.

## Decisión sobre enumeración

Se permite descubrir la identidad mínima de una empresa registrada antes de estar vinculada: es la excepción deliberada necesaria para D2, compatible con el modo de vínculo por id ya existente. La existencia en FullTank es información revelada; no se afirma que esté oculta. No existe consentimiento/flag de vinculabilidad en el modelo actual y no se inventa esa aprobación. El proveedor debe conocer el RUC completo; el backend limita a 10 consultas en una ventana de 60 segundos por providerId y por instancia. Aciertos, inexistentes y consultas repetidas consumen cuota; cambiar de usuario del mismo proveedor no la evita. 429 LOOKUP_RATE_LIMITED usa el recurso de error compartido y Retry-After: 60. La adquisición de cuota es sincronizada; expira con reloj monotónico y el mapa está acotado a 10 000 proveedores activos (nuevas ventanas se rechazan con 429 al alcanzar ese límite).

El límite evita enumeración rápida en una instancia, no elimina descubrimiento lento, cuentas de varios proveedores, reinicios ni varias réplicas. En despliegue horizontal debe agregarse un límite compartido por proveedor en gateway/almacenamiento común. Si la existencia de un comprador se considera confidencial, la alternativa es invitación/aceptación con token o consentimiento persistido; ese cambio de negocio no forma parte de estos endpoints. Esta decisión no convierte el lookup en un directorio general ni cambia los permisos de comprador/admin.

## Vincular el id obtenido

Enviar `POST /api/provider/buyer-companies` con `{"buyerCompanyId":4}`. El proveedor proviene del principal, nunca del cuerpo/URL. El modo existente requiere una organización CUSTOMER activa para ese comprador; una empresa legacy sin ella puede tener lookup 200 y POST 404 y requiere regularización IAM, sin crear una organización por esta lectura. La prueba incluye una empresa registrada con organización válida, sin pedidos previos.

201 usa ProviderBuyerCompanyResource y ahora agrega ruc/sector, también en GET del listado de compradores vinculados:

```json
{"id":4,"name":"Comprador SAC","ruc":"20123456789","sector":"Industrial","organizationId":6,"tankCount":0,"criticalTankCount":0,"activeOrderCount":0,"historicalOrderCount":0,"sites":[{"id":10,"customerAccountId":7,"name":"Planta","address":"Lima"}]}
```

409 `PROVIDERBUYERLINK_CONFLICT` distingue vínculo explícito repetido de `BUYERCOMPANY_CONFLICT` por RUC ya registrado al intentar crear una empresa. Ambos códigos ya existían; se añaden pruebas del contrato. Tras vincular, ProviderBuyerAccess habilita tanques únicamente para ese proveedor; otro proveedor conserva 404 hasta una relación propia. Un segundo proveedor también puede buscar la identidad mínima y vincularla: las relaciones no son exclusivas en el modelo existente. No se permite acceder a sus activos solo por conocer el id.

Trazabilidad: [chapter3 develop](https://github.com/16518-IoT-PrimeFuel/Report/blob/develop/docs/chapter3.md), US-31 aporta identidad/RUC/sector y conserva contadores existentes para clientes vinculados; US-32 requiere historial de pedidos/cantidades/fechas, fuera de este lookup; US-51 requiere asociación dispositivo/producto/umbral, fuera de esta lectura. [chapter5 §5.4](https://github.com/16518-IoT-PrimeFuel/Report/blob/develop/docs/chapter5.md), Clientes y tanques: se resuelve selección del comprador registrado, no renderizado, mensajes vacíos ni flujo IoT completo. El filtro por RUC solicitado para el frontend se apoya en el campo agregado; el texto actual de US-31 no lo enumera como criterio explícito.

No hay migración: RUC/sector ya están en buyer_companies y V38 persiste vínculos. ProviderBuyerLookupTest incluye lookup → POST con id devuelto, códigos 409 distinguibles, aislamiento operativo frente a otro proveedor, roles denegados, inexistente/formato/ausencia y cuota. No se compiló ni ejecutó Maven por instrucción del usuario; Claude revisará las pruebas. Snapshot actualizado contractualmente, pendiente de contraste con /api-docs generado.
