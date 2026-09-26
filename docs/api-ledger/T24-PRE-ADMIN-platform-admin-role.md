# T24-PRE-ADMIN — Rol administrativo de plataforma

## Contrato

`POST /api/v2/admin/users/{userId}/promote` requiere `ROLE_ADMIN` y agrega ese rol al usuario sin
eliminar los roles existentes. Devuelve `200` con `{userId, username, roles}`; devuelve `403` para
quien no es administrador y `404` si el usuario no existe. Repetir la promoción es seguro.

## Primer administrador

La migración `V26` agrega `ROLE_ADMIN` al ENUM de MySQL e inserta el rol. El handler existente de
inicio de aplicación siembra los valores de `Roles`; por ello, H2 también crea el registro durante
las pruebas. Para habilitar al primer administrador en producción, edita la primera línea de
`scripts/seed-first-admin.sql` con el username y ejecútalo una sola vez contra la base elegida:

```sql
SET @admin_username = 'admin@example.com';
SOURCE scripts/seed-first-admin.sql;
```

El seed usa `NOT EXISTS`, por lo que repetirlo no duplica la relación. Verifica que el usuario y
`ROLE_ADMIN` existan antes de ejecutarlo.

## Token

`UserDetailsImpl.build(User)` deriva authorities de los roles persistidos. La promoción actualiza
`user_roles`, pero no modifica JWT ya emitidos: el usuario debe iniciar sesión otra vez para obtener
un token que contenga `ROLE_ADMIN`.

## Verificación

`AdminUsersV2ControllerTest` cubre no-admin (403), promoción, repetición, roles conservados,
usuario inexistente (404) y acceso de un admin a `GET /api/v1/users`.
