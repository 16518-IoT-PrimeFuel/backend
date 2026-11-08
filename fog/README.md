# FullTank Fog API

Recibe las lecturas del sensor de nivel (ESP32) por **HTTP** o **MQTT**, las valida y las reenvía a la ingesta del
backend (`POST /api/telemetry/readings`) con el `X-Device-Token`. El token vive solo aquí: nunca viaja por el broker.

```
ESP32 ──MQTT──▶ broker.hivemq.com (complemento) ──▶ Fog API ──HTTPS + X-Device-Token──▶ backend
ESP32 ──HTTP (misma red)──────────────────────────▶ Fog API
```

Guía paso a paso (compilar, correr, desplegar y probar con el sensor): abrir [`GUIA.html`](GUIA.html) en el navegador.

## Endpoints

| Método | Ruta | Uso |
|---|---|---|
| `POST` | `/api/v1/readings` | Entrada HTTP del dispositivo. Cabecera `X-Fog-Key`. 202 = encolada, 400 = descartar, 401 = clave inválida |
| `GET` | `/api/v1/readings/recent` | Últimas 50 lecturas y su resultado en el backend (`quality`) |
| `GET` | `/health` | Estado de MQTT, backend (sin secretos) y cola |

El cuerpo es el mismo contrato del backend:

```json
{"schemaVersion":1,"deviceId":"esp32-tank-1","channel":"level","sequence":1001,
 "capturedAt":"2026-10-22T15:00:00Z","level":640.5,"unit":"LITRE"}
```

## Variables de entorno

| Variable | Por defecto | Descripción |
|---|---|---|
| `BACKEND_URL` | `https://fulltank-backend.onrender.com/api` | Base de la API del backend |
| `DEVICE_TOKEN` | — | Token emitido con `POST /api/provider/tanks/{tankId}/device-credential` |
| `FOG_API_KEY` | — | Clave para `POST /api/v1/readings`. Vacía = sin control (solo pruebas) |
| `MQTT_ENABLED` | `true` | `false` para usar solo HTTP |
| `MQTT_URL` | `tcp://broker.hivemq.com:1883` | Broker público HiveMQ (complemento) |
| `MQTT_TOPIC` | `fulltank/change-me/tanks/+/readings` | Usar un sufijo aleatorio en vez de `change-me` |
| `ALLOWED_DEVICE_IDS` | — | Lista separada por comas. Vacía = cualquiera |
| `MAX_LEVEL` | `100000` | Nivel máximo aceptado (capacidad del tanque) |
| `PORT` | `8090` | Puerto HTTP |

## Ejecutar

```bash
./mvnw test
./mvnw package -DskipTests
DEVICE_TOKEN=... FOG_API_KEY=... MQTT_TOPIC=fulltank/<sufijo>/tanks/+/readings java -jar target/fulltank-fog-0.0.1-SNAPSHOT.jar
```

Con Docker:

```bash
cp .env.example .env   # completar DEVICE_TOKEN, FOG_API_KEY y MQTT_TOPIC
docker compose up -d --build
docker compose logs -f fog
```

**Plan B** (HiveMQ caído o la red bloquea el 1883): el ESP32 envía por HTTP directo a
`http://<IP-de-tu-PC>:8090/api/v1/readings` con `X-Fog-Key`. No depende de ningún broker.

## Comportamiento

- Reenvía en orden de llegada. Si el backend no responde (Render dormido, 5xx, timeout de 90 s), reintenta la
  **misma** lectura con espera creciente (2 s → 60 s); las siguientes esperan detrás.
- Un 4xx del backend descarta la lectura. Un 202 con `quality: QUARANTINED` se registra como aviso: revisar
  `DEVICE_TOKEN` y el vínculo del tanque.
- Rechaza `deviceId` no permitidos, niveles fuera de rango, `schemaVersion` distinta de 1 y `capturedAt` en el futuro.
- Ignora una `sequence` ya recibida (p. ej. llega por MQTT y por HTTP).
- Cola en memoria (500 lecturas); si se llena, descarta la más antigua. Se pierde al reiniciar.

## Limitaciones del broker público

Cualquiera puede suscribirse al topic (ver niveles) o publicar en él (lecturas falsas). Mitigación: topic con sufijo
aleatorio, `ALLOWED_DEVICE_IDS`, validación de rango y sin credenciales en los mensajes. Para producción: broker
privado con TLS, usuario por dispositivo y ACL por topic.
