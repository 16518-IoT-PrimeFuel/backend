# FullTank Backend

API del proyecto FullTank con Spring Boot 4.0.6 y Java 26.

## Requisitos

- JDK 26
- MySQL 8
- Maven Wrapper incluido en el repositorio

## Configuración local

Configura estas dos variables antes de iniciar la aplicación:

- `AUTHORIZATION_JWT_SECRET`: secreto aleatorio de al menos 32 bytes. Spring lo carga con `Keys.hmacShaKeyFor`.
- `DATABASE_PASSWORD`: contraseña del usuario MySQL.

Las demás variables tienen valores predeterminados para desarrollo: `DATABASE_URL=localhost`, `DATABASE_PORT=3306`, `DATABASE_NAME=fulltank_platform`, `DATABASE_USER=root` y `PORT=8080`.

En PowerShell:

```powershell
$env:JAVA_HOME = 'C:\ruta\a\jdk-26'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:AUTHORIZATION_JWT_SECRET = 'reemplazar-por-un-secreto-aleatorio-de-32-bytes-minimo'
$env:DATABASE_PASSWORD = 'contraseña-local'
./mvnw.cmd spring-boot:run
```

Swagger UI está en [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) y el documento OpenAPI en `/api-docs`.

## Primer administrador

Crea el primer administrador con el procedimiento manual de `scripts/seed-first-admin.sql`. Inicia sesión de nuevo después del cambio para obtener un JWT con los roles actualizados.

## Esquema de base de datos

Flyway es responsable de los cambios de esquema (V1–V32). Hibernate usa `ddl-auto=validate`; no crea ni modifica tablas en ejecución. No edites migraciones que ya se hayan aplicado: agrega una nueva migración con el siguiente número. Las tablas `drivers` y `vehicles` se mantienen porque Fleet v2 las utiliza.
