# T17-B — Integración de decisión safety al lifecycle de delivery

## Estado: detenido por una decisión de producto faltante

La evaluación pura de geocerca existe en `safety.api.GeofenceEvaluation`. La regla U09 está resuelta: posición fresca hasta cinco minutos, accuracy máxima de 50 m, borde deniega y ausencia de política produce `NO_POLICY`. La decisión debe persistirse y publicarse con el registro transaccional de eventos. El modo de detección registra `AUTHORIZED` o `BLOCKED`, pero nunca impide la transición física. No se añade endpoint público de decisión ni override.

## Ambigüedad que bloquea el cableado

S14 define la cadena `ASSIGNED → STARTED → ARRIVED → DELIVERING → COMPLETED` y su API no tiene un comando separado para iniciar descarga: cerrar desde `ARRIVED` registra también `DELIVERING`. S17/U09 y T17-A dicen que la decisión se integra al avanzar el lifecycle, pero no identifican cuál transición debe disparar la evaluación. Evaluar al entrar en `DELIVERING` parece plausible por tratarse del estado de descarga, pero no existe aprobación documental para fijar ese disparador. Elegir otro punto cambia el significado de la evidencia y cuándo se registra `SafetyDecision`.

Por la regla explícita de T17-B, no se modifica el flujo de producción ni se escriben tests que den por aprobada una transición. Falta decidir qué transición dispara la evaluación (por ejemplo, `ARRIVED → DELIVERING`) antes de continuar.

## Trabajo inequívoco verificado

- La seam `GeofenceEvaluation` recibe el id de delivery y evalúa la última evidencia de tracking; la implementación T17-A persiste la decisión y publica el evento safety mediante `EventPublicationRegistry`.
- No hay endpoint de lectura pública de decisiones, ni debe agregarse en este ticket.
- T18-A/B y T21-A/B permanecen fuera de ejecución mientras T6 no quede completo, según el orden del objetivo.

No se añade ruta ni migración. Los escenarios de persistencia atómica, evento, tenant y transición de detección quedan pendientes junto con la decisión del disparador.
