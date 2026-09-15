# Bounded Context IAM - FullTank Platform

## 1. Estado del bounded context

El bounded context **IAM (Identity and Access Management)** ya está implementado en el backend. Su responsabilidad es registrar y autenticar usuarios, asociarlos a una empresa compradora o proveedora, emitir JWT, resolver roles y aplicar reglas de ownership sobre los recursos protegidos.

La implementación sigue una separación por capas:

```text
Interfaces -> Application -> Domain <- Infrastructure
```

La capa Domain define el lenguaje y los puertos. Application coordina los casos de uso. Infrastructure implementa persistencia, BCrypt, JWT y Spring Security. Interfaces expone REST y el ACL que consumen otros bounded contexts.

## 2. Límites y casos de uso

| Capacidad | Implementación actual |
|---|---|
| Inicio de sesión | `POST /api/v1/authentication/sign-in`; valida credenciales y devuelve JWT, roles y company/provider ID. |
| Registro | `POST /api/v1/authentication/sign-up`; crea usuario y una empresa nueva de forma transaccional. |
| Recuperación | `POST /api/v1/authentication/password-reset/request` y `/confirm`; token hash, expiración de 30 minutos y uso único. |
| Directorio | Usuarios, compañías compradoras y compañías proveedoras mediante controladores REST y servicios de consulta. |
| Autorización | Filtro Bearer JWT y `CurrentUserAccess` para roles y ownership por compañía, proveedor o usuario. |
| Integración | `IamContextFacade` expone el acceso controlado del contexto a otros módulos. |

El registro no acepta `companyId` ni `providerId` elegidos por el cliente. El backend genera el ID, valida RUC único y exige un único rol compatible con el perfil enviado.

## 3. Domain Layer

| Clase / componente | Tipo | Propósito |
|---|---|---|
| `User` | Aggregate Root | Representa la identidad autenticable, sus roles y el vínculo con la compañía compradora o proveedora. |
| `BuyerCompany` | Aggregate Root | Modela la empresa que solicita combustible y sus datos de contacto. |
| `ProviderCompany` | Aggregate Root | Modela la empresa proveedora, combustibles ofrecidos y descripción comercial. |
| `Role` | Entity | Representa un rol persistible asociado a un usuario. |
| `Roles` | Value Object / enum | Define `ROLE_BUYER` y `ROLE_PROVIDER`. |
| `SignUpCommand`, `SignInCommand` | Command | Expresan los casos de uso de registro e inicio de sesión. |
| `CreateBuyerCompanyCommand`, `CreateProviderCompanyCommand` | Command | Transportan la creación de perfiles de empresa. |
| `SeedRolesCommand` | Command | Inicializa los roles base de IAM. |
| `Get*Query` | Query | Consultan usuarios y compañías por ID, username o listado. |
| `UserRepository` | Repository Port | Contrato de persistencia para usuarios. |
| `RoleRepository` | Repository Port | Contrato de persistencia para roles. |
| `BuyerCompanyRepository` | Repository Port | Contrato para compañías compradoras, incluido RUC único. |
| `ProviderCompanyRepository` | Repository Port | Contrato para compañías proveedoras, incluido RUC único. |

## 4. Application Layer

| Clase / componente | Tipo | Propósito |
|---|---|---|
| `UserCommandService` / `UserCommandServiceImpl` | Command Service / Handler | Ejecuta sign-in, sign-up, hashing, roles y vínculos de empresa. |
| `UserQueryService` / `UserQueryServiceImpl` | Query Service | Consulta usuarios para directorio y autorización. |
| `BuyerCompanyCommandService` / `Impl` | Command Service | Crea y modifica compañías compradoras. |
| `BuyerCompanyQueryService` / `Impl` | Query Service | Lista y consulta compañías compradoras. |
| `ProviderCompanyCommandService` / `Impl` | Command Service | Crea y modifica compañías proveedoras. |
| `ProviderCompanyQueryService` / `Impl` | Query Service | Lista y consulta compañías proveedoras. |
| `RoleCommandService` / `RoleCommandServiceImpl` | Command Service | Ejecuta la siembra de roles del sistema. |
| `PasswordResetService` | Application Service | Genera tokens aleatorios, guarda solo el hash, envía correo y cambia la contraseña. |
| `HashingService` | Outbound Port | Abstrae BCrypt para contraseñas. |
| `TokenService` | Outbound Port | Abstrae la emisión y lectura de JWT. |

## 5. Infrastructure Layer

| Clase / componente | Tipo | Propósito |
|---|---|---|
| `UserRepositoryImpl`, `RoleRepositoryImpl` | Repository Adapter | Adaptan puertos de Domain a Spring Data JPA. |
| `BuyerCompanyRepositoryImpl`, `ProviderCompanyRepositoryImpl` | Repository Adapter | Persisten empresas y consultas de RUC. |
| `*PersistenceEntity` | JPA Entity | Representan tablas `users`, `roles`, `buyer_companies`, `provider_companies`. |
| `PasswordResetTokenEntity` | JPA Entity | Guarda hash, usuario y expiración del token de recuperación. |
| `*PersistenceRepository` | Spring Data JPA | Ejecutan consultas persistentes. |
| `PasswordResetTokenRepository` | Spring Data JPA | Busca tokens válidos con bloqueo y elimina tokens usados. |
| `*PersistenceAssembler` | Mapper | Convierte entre agregados Domain y entidades JPA. |
| `WebSecurityConfiguration` | Security Configuration | Configura endpoints públicos, sesiones stateless y filtro JWT. |
| `BearerAuthorizationRequestFilter` | JWT Filter | Lee el bearer token y carga la autenticación. |
| `UserDetailsServiceImpl` | Security Adapter | Traduce el usuario de Domain a Spring Security. |
| `CurrentUserAccess` | Ownership Policy | Comprueba rol y pertenencia a compañía, proveedor o usuario. |
| `HashingServiceImpl` | BCrypt Adapter | Implementa `HashingService`. |
| `TokenServiceImpl` | JWT Adapter | Implementa `TokenService`. |

## 6. Interfaces Layer

| Clase / componente | Tipo | Propósito |
|---|---|---|
| `AuthenticationController` | REST Controller | Expone sign-in, sign-up y password reset. |
| `UsersController` | REST Controller | Expone el directorio administrativo de usuarios. |
| `BuyerCompaniesController` | REST Controller | Expone operaciones de compañías compradoras. |
| `ProviderCompaniesController` | REST Controller | Expone operaciones de compañías proveedoras. |
| `DirectoryController` | REST Controller | Expone consultas de directorio para proveedores. |
| `*Resource` | DTO record | Define los cuerpos JSON de entrada y salida. Incluye validación Bean Validation en autenticación. |
| `*ResourceFromEntityAssembler` | Assembler | Convierte agregados y valores de Domain a DTOs REST. |
| `*CommandFromResourceAssembler` | Assembler | Convierte requests REST a comandos de Application. |
| `IamContextFacade` | ACL | Punto de acceso controlado del contexto para otros módulos. |

## 7. Seguridad y reglas verificadas

- El secreto JWT es obligatorio mediante `AUTHORIZATION_JWT_SECRET`.
- Los endpoints de autenticación y documentación son públicos; el resto requiere autenticación.
- Un usuario comprador solo puede operar sobre su `companyId`; un proveedor sobre su `providerId`.
- Las notificaciones exigen exactamente un destinatario y no permiten mezclar IDs de otra identidad.
- El reset de contraseña devuelve una respuesta neutra para cuentas existentes y desconocidas.
- El token de reset se almacena como SHA-256, expira en 30 minutos y se elimina al usarlo.
- SMTP se configura con `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS` y `MAIL_FROM`.

## 8. Artefactos y reproducción

- `docs/diagrams/iam-bounded-context.puml`: mapa de límites y relaciones del bounded context.
- `docs/diagrams/iam-layer-overview.puml`: vista visual de Interfaces, Application, Domain e Infrastructure.
- `docs/diagrams/iam-class-layer.puml`: vista detallada de clases y relaciones.
- `docs/diagrams/iam-structurizr.dsl`: modelo equivalente para Structurizr.
- `tools/plantuml/plantuml.jar`: compilador PlantUML incluido en el proyecto.
- `tools/structurizr/structurizr.sh`: CLI Structurizr incluido en el proyecto.

Comandos de render:

```bash
java -jar tools/plantuml/plantuml.jar -tpng -tsvg -o rendered docs/diagrams/iam-bounded-context.puml
java -jar tools/plantuml/plantuml.jar -tpng -tsvg -o rendered docs/diagrams/iam-layer-overview.puml
tools/structurizr/structurizr.sh validate -workspace docs/diagrams/iam-structurizr.dsl
tools/structurizr/structurizr.sh export -workspace docs/diagrams/iam-structurizr.dsl -format plantuml -output docs/diagrams/rendered/structurizr
```
