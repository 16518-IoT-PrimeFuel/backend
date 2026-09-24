# T02-A — Spike y reglas de dependencia

Estado: baseline inicial completado el 2026-09-23.

## Decisión

Se mantiene el monolito modular sin introducir Spring Modulith todavía. El
proyecto usa Spring Boot `4.0.6`; añadir un verificador cuya compatibilidad con
esta línea no esté comprobada agregaría riesgo al refactor. La primera barrera
se implementa con una prueba JUnit sin dependencia adicional.

La prueba `ModuleBoundaryBaselineTest` permite reducir la deuda conocida, pero
falla si aparece una violación nueva.

## Reglas iniciales

1. `domain` y `application` no importan `infrastructure` ni `interfaces`.
2. Las entidades JPA y los controllers permanecen en adapters internos.
3. Las relaciones entre bounded contexts se moverán a contratos `api/events`.
4. `shared` solo puede contener capacidades técnicas verdaderamente
   transversales; no se usará como cajón para reglas de negocio.
5. Toda excepción temporal debe estar nombrada en el baseline y tener ticket
   de salida.

## Deuda AS-IS congelada

Tras extraer el puerto de tokens de password reset, quedan tres cruces
internos conocidos:

- `ordering.application.FuelRequestService` importa entidad y repositorio JPA.
- `ordering.application.FuelRequestService` importa un resource REST.

Estos cruces no se corrigen en T02-A; quedan para los tickets de extracción del
bounded context dueño. La prueba impide que aumenten.

## Gate de salida

- la regla arquitectónica falla ante una violación sembrada;
- la suite existente sigue verde;
- no se agregó una dependencia de verificación no validada;
- las excepciones actuales tienen dueño y ticket de eliminación.
