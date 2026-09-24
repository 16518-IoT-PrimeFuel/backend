# T04-A — Organizaciones y memberships tenant

Estado: expansión aditiva y backfill idempotente implementados y verificados.

Se añadieron `organizations` y `memberships` con estado `ACTIVE/INACTIVE`,
roles de membership y referencias explícitas a `legacy_buyer_company_id` y
`legacy_provider_company_id`. El ACL tenant consulta memberships activas cuando
el usuario ya tiene alguna; usuarios aún no migrados conservan el fallback a
los IDs legacy del principal.

También se añadieron endpoints v2 aditivos para invitar y revocar usuarios
existentes bajo `/api/v2/provider-companies/{providerId}/memberships`. Ambos
requieren que el principal sea dueño del proveedor indicado.

Esto permite backfill gradual y evita inferir una relación desde la última
orden o desde `favoriteProviderId`. La migración todavía no elimina columnas
legacy ni cambia los contratos v1.

## Verificación

- migración Flyway V4 sobre H2;
- usuario con membership activa solo accede a su organización;
- IDs legacy distintos son rechazados cuando ya existen memberships;
- usuarios sin memberships mantienen compatibilidad v1;
- suite completa verde.

## Pendiente

El fallback legacy se conserva deliberadamente hasta validar la conciliación en
la base MySQL real y completar fixtures persistentes A/B.
