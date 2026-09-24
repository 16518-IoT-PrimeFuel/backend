# T01-B — Caracterización de estados y seguridad

Estado: completado el 2026-09-23.

## Evidencia ejecutable

Comando:

```bash
JAVA_HOME=/tmp/jdk-26.0.2.1 PATH=/tmp/jdk-26.0.2.1/bin:$PATH \
  bash mvnw -Dmaven.repo.local=/tmp/fulltank-m2 test
```

Resultado: **13 tests, 0 failures, 0 errors**.

La suite cubre:

- arranque de aplicación con H2 aislado;
- signup público y asociación de compañía propia;
- rechazo de `companyId` enviado por el cliente durante signup;
- solicitud y confirmación de reset de contraseña de un solo uso;
- no revelación de cuentas inexistentes en password reset;
- buyer contra buyer-company ajena: `403`;
- provider contra provider-company ajena: `403`;
- provider contra buyer-company: `403`;
- buyer no puede crear notificación para otro usuario;
- reglas unitarias de ownership buyer/provider;
- conteo runtime de **77 rutas `/api/v1`**.

## Comportamiento observado

Este ticket caracteriza el AS-IS; no convierte todos los resultados actuales en
reglas deseadas para la arquitectura destino.

| Área | Observación | Clasificación |
|---|---|---|
| Identidad | El principal contiene `companyId` o `providerId` directamente | `known-gap` para S04 |
| Tenant | El acceso se resuelve con `CurrentUserAccess` basado en roles globales | `known-gap` para S04 |
| Compañías | `buyer-companies` y `provider-companies` siguen dentro de IAM | `known-gap` para S05 |
| Rutas v1 | Las 77 operaciones están expuestas bajo `/api/v1` | contrato a preservar |
| Datos | Las pruebas usan H2 y `ddl-auto=create-drop` | entorno de caracterización |
| Logística | No existe aún cobertura de concurrencia ni carreras de aceptación/reserva | gap bloqueante antes de mover Ordering/Fulfillment |
| Telemetría | No existe ingestión de dispositivos | fuera de T01 |

## Criterio de salida

- El baseline compila y arranca en perfil de prueba.
- Las rutas y estados observados tienen evidencia automatizada mínima.
- Los casos negativos de ownership A/B están registrados.
- Los defectos observados quedan etiquetados y no se usan como invariantes
  destino.

## Rollback

Eliminar este harness y sus pruebas adicionales no cambia datos ni contratos de
producción.
