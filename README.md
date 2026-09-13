# backend

## Local setup

The project targets Java 26. Set `AUTHORIZATION_JWT_SECRET` to a private random
value of at least 32 bytes before starting the API; there is no development
fallback secret. Configure `DATABASE_URL`, `DATABASE_PORT`, `DATABASE_NAME`,
`DATABASE_USER`, and `DATABASE_PASSWORD` for the MySQL instance, then run
`./mvnw spring-boot:run`.
