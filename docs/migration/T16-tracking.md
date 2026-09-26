# T16 — Tracking de delivery

Estado: ingestión append-only inicial implementada y verificada el 2026-09-24.

`POST /api/v2/deliveries/{deliveryId}/tracking` acepta puntos solo para el
proveedor dueño de la entrega y cuando esta está `DISPATCHED` o `ARRIVED`. Las
coordenadas y velocidad se validan, y `eventId` es único para que los reintentos
no dupliquen lecturas. `GET` devuelve la traza ordenada y `/latest` la última
muestra.

No se habilitan geocerca, tracking público ni comandos físicos en este ticket.
Esos comportamientos requieren política de privacidad, reloj/protocolo y
evidencia de dispositivo antes de activar prevención.
