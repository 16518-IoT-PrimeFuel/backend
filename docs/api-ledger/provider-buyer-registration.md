# Alta y vínculo de comprador antes del primer pedido

`POST /api/provider/buyer-companies` requiere ROLE_PROVIDER. El proveedor sale del principal; nunca se acepta providerId/organizationId para ampliar acceso.

Nuevo comprador:
```json
{"name":"Nuevo Comprador SAC","ruc":"20999888777","sector":"Industrial","address":"Planta Lima","contactEmail":"operaciones@comprador.test","phone":"999000111","siteName":"Planta"}
```
name y ruc son obligatorios al crear; RUC de 11 dígitos y único. Los demás campos son opcionales; el sitio toma el nombre de la empresa si siteName se omite.

Comprador existente, sin historial comercial:
```json
{"buyerCompanyId":4}
```
Debe existir su organización compradora activa con el mismo RUC. El vínculo es explícito y pertenece al proveedor autenticado; un segundo proveedor necesita registrar su propio vínculo. Esta operación no concede membresía ni crea usuario, contraseña o token IAM.

201, mismo schema que un ítem de GET /api/provider/buyer-companies:
```json
{"id":4,"name":"Nuevo Comprador SAC","organizationId":6,"tankCount":0,"criticalTankCount":0,"activeOrderCount":0,"historicalOrderCount":0,"sites":[{"id":10,"customerAccountId":7,"name":"Planta","address":"Planta Lima"}]}
```

Luego POST /api/provider/tanks usa buyerCompanyId=4, customerAccountId=7 y siteId=10. Ya no necesita un pedido o solicitud previo. GET compradores/tanques, PUT tanque, lecturas y episodios reconocen vínculos explícitos además del historial anterior. Los contratos JSON de esas rutas conservan sus campos.

400 datos inválidos; 403 rol incorrecto; 404 comprador existente no encontrado; 409 RUC o relación proveedor/comprador duplicados. Los errores usan el ErrorResource existente: {"code":"VALIDATION_ERROR","message":"...","details":"..."}. Todos los cambios se guardan en una transacción.

V38__provider_buyer_links.sql agrega la relación con claves foráneas y unicidad (provider_id,buyer_company_id). Las invitaciones de IAM son membresía de organización, no una relación comercial, y el backend no tiene un contrato comercial reutilizable. US-51 permite asociar tanque/dispositivo antes del primer pedido; las pantallas y la provisión de hardware del cap. 5 siguen pendientes.
