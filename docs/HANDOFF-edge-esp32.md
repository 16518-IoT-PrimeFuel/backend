# HANDOFF: ESP32 → Edge API → Backend (FullTank / PrimeFuel)

> Para una nueva sesión de Claude Code (laptop de Samuel, en clase con Felipe y la placa).
> Fecha del handoff: 22/10/2026. Rama: `feat/edge-broker`. Entrega: hoy 22/10 a las 23:00.
> Léelo completo antes de actuar. Todo lo que dice "verificado" se comprobó en el código o con pruebas reales.

---

## 0. Reglas del usuario (obligatorias)

- Responder **en español**.
- **Ningún commit, push ni despliegue sin el visto bueno explícito** de Samuel.
- Commits en **inglés**, con Conventional Commits.
- Trabajar en la rama **`feat/edge-broker`**.
- **No hay telemetría de camiones**: el único IoT es el sensor de nivel del tanque.
- No usar brokers públicos con credenciales ni datos personales. El `X-Device-Token` **nunca** pasa por el broker.

---

## 1. Objetivo de la sesión en clase

Conectar el **ESP32 real** de Felipe (con sensor ultrasónico JSN-SR04T; en simulación, HC-SR04) con la **Edge API**, que
corre en Docker en la laptop de Samuel, y verificar que cada lectura llegue al **backend en Render** como
`quality: ACCEPTED` y que cambie el nivel del tanque en la web.

Si algo falla, diagnosticar por capas, en este orden: **firmware → red/broker → Edge → backend** (ver §9).

---

## 2. Arquitectura (decidida y justificada)

```
ESP32 ──MQTT──▶ broker.hivemq.com:1883 ──▶ Edge API (laptop, Docker :8090) ──HTTPS + X-Device-Token──▶ Backend Render
                plan B: Mosquitto (mismo docker-compose, :1883)
ESP32 ──HTTP (misma WiFi)──▶ POST http://<IP-laptop>:8090/api/v1/readings  (cabecera X-Edge-Key)
```

**Por qué esta arquitectura:**
- El profesor dijo: *MQTT es complementario; la integración obligatoria del dispositivo es con una **Edge API desarrollada por el equipo**; se puede usar un broker público con plan B (Mosquitto/EMQX en Docker); hay que documentar en "Solution Software Design" la elección del broker y sus limitaciones de seguridad.*
- **La Edge NO va en Render**: el plan gratis se duerme a los 15 min (el suscriptor MQTT dejaría de escuchar) y los background workers no tienen plan gratis (verificado en render.com/docs/free).
- **La Edge NO va dentro de `src/`** como bounded context: es otra unidad de despliegue. Se describe como "adaptador de borde del bounded context `telemetry`". El firmware es C++ y tampoco va ahí.
- **No hace falta un repo aparte** ni reescribir el puente en Node o Python.
- **El backend no se modificó.**

---

## 3. Estado actual: qué hay en la rama

Todo está en commits ya subidos a `origin/feat/edge-broker` (ver §11). Estructura:

```
backend/
├── README.md                    sección "Componentes del repositorio" (nueva)
├── docs/plan-edge-mqtt.html     plan visual (opcional en el commit)
├── docs/HANDOFF-edge-esp32.md   este archivo
├── edge/                        Edge API (Spring Boot 4.0.6, Java 26, Eclipse Paho 1.2.5)
│   ├── GUIA.html                guía visual: compilar, correr, desplegar y probar
│   ├── README.md
│   ├── docker-compose.yml       edge (:8090) + eclipse-mosquitto:2.0.20 (:1883, sin anónimos)
│   ├── Dockerfile               eclipse-temurin:26
│   ├── .env.example             plantilla (el .env real está en .gitignore)
│   ├── mosquitto/mosquitto.conf
│   └── src/main/java/com/primefuel/fulltank/edge/
│       ├── EdgeApplication.java
│       ├── config/EdgeProperties.java
│       ├── domain/Reading.java, ReadingPolicy.java
│       ├── application/ReadingRelayService.java (cola + reintentos), TelemetryForwarder.java, ...
│       ├── infrastructure/BackendTelemetryClient.java (RestClient → backend), MqttReadingSubscriber.java
│       └── interfaces/rest/ReadingsController.java, HealthController.java
└── firmware/
    ├── README.md
    └── fulltank_sensor/
        ├── fulltank_sensor.ino   firmware de referencia (compila para esp32dev sin advertencias)
        ├── diagram.json          circuito Wokwi (board-esp32-devkit-c-v4 + HC-SR04 + LED)
        ├── libraries.txt         PubSubClient
        └── secrets.example.h     plantilla; secrets.h está en .gitignore
```

**Verificado:**
- Edge: 14/14 pruebas pasan (`./mvnw test`).
- Prueba de humo real:
  - HTTP sin clave → 401; nivel negativo → 400; `deviceId` no permitido → 400.
  - HTTP y MQTT por **broker.hivemq.com** llegan a la Edge y se reenvían.
  - Con el backend apagado, las lecturas quedan en cola y se entregan solas al volver (una al tercer intento).
  - Plan B con docker-compose: Mosquitto rechaza conexiones anónimas (código 5) y acepta con credenciales; la lectura llega al backend con el token.
  - Todo esto se probó contra un **backend falso local**, no contra Render. **Falta la prueba real con Render y el token real.**
- Firmware de referencia: **compila** (PlatformIO, `esp32dev`). **No se simuló en Wokwi ni se probó en la placa.**
- **Felipe está escribiendo su propio firmware** en Arduino IDE. Hay que compararlo con el contrato de §5 y decidir cuál queda en el repo.

**Corrección hecha en la Edge** (para que no se revierta): `BackendTelemetryClient` usa **HTTP/1.1** +
`BufferingClientHttpRequestFactory` para enviar `Content-Length`. Antes enviaba el cuerpo en chunks y un servidor lo rechazó.

---

## 4. Hechos verificados del backend (commit base 9dfc819)

- Ingesta: `POST /api/telemetry/readings`, ruta pública, autenticada con la cabecera **`X-Device-Token`** (no JWT).
- **Responde 202 aunque falle la autenticación**: token malo o revocado, o sin vínculo activo → 202 con `quality: QUARANTINED`. **Un 202 no prueba éxito: mirar `quality`.**
- Para que la lectura quede `ACCEPTED`, el **`deviceId` y el `channel` de la lectura deben ser exactamente los de la credencial** (`DeviceAuthenticationImpl`: si no, `UNKNOWN_CREDENTIAL`). También debe haber un vínculo activo en `capturedAt`.
- El `deviceId` y el `channel` se definen **al registrar el tanque** (`RegisterProviderTankResource`: `deviceId` máx. 120, `channel` máx. 60, `capacity`, `unit` LITRE|GALLON).
- **Si el nivel supera la capacidad del tanque, la lectura queda en cuarentena.**
- Se descarta una lectura con (deviceId, channel, sequence) ya recibida (`sequence_number bigint`, único por dispositivo y canal).
- El nivel del tanque ignora lecturas con `capturedAt` igual o anterior a la última aplicada.
- `unit`: `GAL/GALLON/...` → GALLON; cualquier otro valor → LITRE.
- Endpoints útiles:
  - Login: `POST /api/authentication/sign-in` con `{"username","password"}` → `token` (JWT).
  - Emitir credencial: `POST /api/provider/tanks/{tankId}/device-credential` (distribuidor dueño, JWT) → `tankId, deviceId, channel, credentialId, tokenVersion, token, issuedAt`. **El token se muestra una sola vez.** Responde 409 si ya hay uno activo.
  - Rotar: `POST .../device-credential/rotate`. Revocar: `POST .../device-credential/revoke`.
  - Ver lecturas: `GET /api/provider/tanks/{tankId}/readings` (JWT).
- Base URL: `https://fulltank-backend.onrender.com/api`. Render gratis se duerme a los 15 min; el primer request tarda cerca de un minuto.

---

## 5. CONTRATO dispositivo ↔ Edge (lo que el firmware de Felipe debe cumplir)

### MQTT (Wokwi o placa)
- Broker: `broker.hivemq.com:1883`, sin usuario. Plan B: `<IP-laptop>:1883` con usuario y clave de Mosquitto.
- Topic: `fulltank/<SUFIJO>/tanks/<tankId>/readings`. **El SUFIJO tiene que ser igual al de `MQTT_TOPIC` en el `.env` de la Edge** (la Edge se suscribe a `fulltank/<SUFIJO>/tanks/+/readings`, QoS 1).

### HTTP (placa en la misma WiFi que la laptop)
- `POST http://<IP-laptop>:8090/api/v1/readings`
- Cabeceras: `Content-Type: application/json` y `X-Edge-Key: <EDGE_API_KEY>`
- Respuestas: **202** = encolada (OK) · **400** = inválida, descartar · **401** = clave mala, descartar · sin respuesta o **5xx** = reintentar la misma lectura.

### Cuerpo JSON (igual por MQTT y por HTTP)
```json
{"schemaVersion":1,"deviceId":"<deviceId del tanque>","channel":"<channel del tanque>","sequence":1792650000,"capturedAt":"2026-10-22T15:00:00Z","level":640.5,"unit":"LITRE"}
```

| Campo | Regla | Si falla |
|---|---|---|
| `schemaVersion` | `1` | Edge 400 |
| `deviceId`, `channel` | **Exactos** del registro del tanque; `deviceId` además en `ALLOWED_DEVICE_IDS` | Backend QUARANTINED / Edge 400 |
| `sequence` | Entero único y creciente. Recomendado: **hora UNIX** de la captura (Wokwi borra la NVS en cada simulación) | Se ignora como duplicada |
| `capturedAt` | UTC ISO-8601 con `Z`, por NTP; no más de 5 min en el futuro | Edge 400 |
| `level` | **Litros**, entre 0 y la capacidad del tanque (`MAX_LEVEL`) | Edge 400 / Backend QUARANTINED |
| `unit` | `"LITRE"` | — |
| Token | **El ESP32 NO envía `X-Device-Token`.** Lo agrega la Edge | — |

**MQTT con PubSubClient:** `mqtt.setBufferSize(512)` (el JSON supera los 256 bytes por defecto). Client ID único (p. ej. deviceId + aleatorio).

### Hardware (capítulo 5 del informe: Report-IoT `docs/chapter5.md`)
- Placa: **ESP32 DevKit V1**. En Wokwi, plantilla **"ESP32"** (clásica), **no la S3** (en la S3 los GPIO 26–32 no sirven).
- JSN-SR04T: VCC → 5 V (VIN), GND común, **TRIG → GPIO 5**, **ECHO → divisor 1 kΩ / 2 kΩ → GPIO 18** (el eco sale a 5 V).
- LEDs verde / ámbar / rojo → GPIO 25 / 26 / 27 con 220 Ω (47 Ω en la simulación).
- El firmware de referencia usa **solo el LED de GPIO 25** (error). El informe pide 3: alinear si da tiempo.
- Cálculo: mediana de 5 mediciones; `distancia = eco_µs × 0,0343 / 2`; `nivel_cm = (separación + altura útil) − distancia`; % limitado a 0–100; `litros = % × capacidad / 100`. Los parámetros de demo (25 cm, 100 cm, 1000 L) **hay que calibrarlos**, y la capacidad no debe superar la del tanque registrado.

---

## 6. Qué configurar en la laptop (antes de clase)

1. **Traer el código:** `git fetch && git switch feat/edge-broker` (requiere que se haya hecho push, ver §11).
2. **Docker** funcionando (`docker ps`). En Linux, si da *permission denied*: `sudo usermod -aG docker $USER` y volver a iniciar sesión. Sin Docker: JDK 26 y `./mvnw` (o `.\mvnw.cmd` en Windows).
3. **Token del dispositivo** (una vez; ver §4):
   ```bash
   curl -X POST https://fulltank-backend.onrender.com/api/authentication/sign-in -H "Content-Type: application/json" -d '{"username":"<user>","password":"<pass>"}'
   curl -X POST https://fulltank-backend.onrender.com/api/provider/tanks/<tankId>/device-credential -H "Authorization: Bearer <JWT>"
   ```
   Anotar `token`, **`deviceId`** y **`channel`**.
4. **`.env`:**
   ```bash
   cd edge && cp .env.example .env
   ```
   | Variable | Valor |
   |---|---|
   | `BACKEND_URL` | `https://fulltank-backend.onrender.com/api` |
   | `DEVICE_TOKEN` | token del paso 3 |
   | `EDGE_API_KEY` | clave larga (la usa el ESP32 en modo HTTP) |
   | `MQTT_URL` | `tcp://broker.hivemq.com:1883` |
   | `MQTT_USERNAME` / `MQTT_PASSWORD` | **vacíos** con el broker público (no enviar credenciales a un tercero) |
   | `MQTT_TOPIC` | `fulltank/<SUFIJO>/tanks/+/readings` |
   | `MOSQUITTO_USERNAME` / `MOSQUITTO_PASSWORD` | para el plan B (la clave es obligatoria: compose falla si está vacía) |
   | `ALLOWED_DEVICE_IDS` | el `deviceId` del tanque |
   | `MAX_LEVEL` | capacidad del tanque |
5. **Levantar:** `docker compose up -d --build` → `curl http://localhost:8090/health` debe mostrar `mqtt.connected: true` y `backend.deviceTokenConfigured: true`.
6. **IP de la laptop** (para HTTP o plan B): `ip -4 addr` (Linux) o `ipconfig` (Windows). Firewall: permitir 8090 (y 1883 en el plan B) en la red local.

---

## 7. Procedimiento en clase (de menor a mayor)

1. **Despertar Render** (abrir la URL y esperar cerca de 1 min).
2. **Edge sola por HTTP**, desde la laptop:
   ```bash
   curl -X POST http://localhost:8090/api/v1/readings -H "Content-Type: application/json" -H "X-Edge-Key: <EDGE_API_KEY>" \
     -d '{"schemaVersion":1,"deviceId":"<deviceId>","channel":"<channel>","sequence":'$(date +%s)',"capturedAt":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","level":500,"unit":"LITRE"}'
   ```
   Esperado: 202 `QUEUED`, y en `docker compose logs -f edge` → `reenviada: quality=ACCEPTED`.
3. **Revisar el firmware de Felipe contra §5**, campo por campo: deviceId, channel, sufijo, NTP, sequence, litros y `setBufferSize`.
4. **Placa conectada**: monitor serie a 115200 → WiFi OK → NTP OK → `publicado` (o `HTTP Edge -> 202`).
5. **Verificar:** `http://localhost:8090/api/v1/readings/recent` (`FORWARDED`/`ACCEPTED`) → `GET /api/provider/tanks/<tankId>/readings` → la web muestra el nivel nuevo.
6. **Mover un objeto frente al sensor** (30 / 60 / 100 cm): con un cambio ≥ 2 % el firmware de referencia envía al instante.
7. **Plan B** (si el broker público falla o la red de clase bloquea el 1883): §8.
8. **Capturas para el informe:** monitor serie, logs de la Edge, `/health`, `/recent`, la web.

---

## 8. Plan B y alternativas si la red de clase bloquea cosas

- **Broker público caído o puerto 1883 bloqueado:** en `.env` poner `MQTT_URL=tcp://mosquitto:1883` y `MQTT_USERNAME`/`MQTT_PASSWORD` = los de Mosquitto → `docker compose up -d`. El ESP32 apunta a `<IP-laptop>:1883` con esas credenciales.
- **Más simple todavía:** el ESP32 usa **HTTP directo** a `http://<IP-laptop>:8090/api/v1/readings` con `X-Edge-Key`. No depende de ningún broker.
- **La red de clase aísla a los clientes** (el ESP32 no ve la laptop): usar el **hotspot del celular**, con la laptop y el ESP32 en la misma red.
- **Wokwi no llega a la laptop** (solo sale a internet): con Wokwi, usar siempre el broker público.

---

## 9. Diagnóstico por síntoma

| Síntoma | Dónde mirar | Causa probable → solución |
|---|---|---|
| Monitor serie: WiFi no conecta | Firmware | SSID o clave, red de 5 GHz (el ESP32 solo usa 2,4 GHz) |
| NTP se queda esperando | Firmware/red | La red bloquea NTP (UDP 123) → hotspot |
| `MQTT: no conecta (estado -2)` | Red | 1883 bloqueado → plan B o HTTP |
| Publica, pero la Edge no recibe | `.env` / firmware | **Sufijo del topic distinto**; topic mal formado; JSON > 256 bytes sin `setBufferSize(512)` |
| Log de la Edge: `descartado: JSON inválido` | Firmware | Comillas, comas o número mal formateado (p. ej. `nan`) |
| Edge 400 `deviceId no autorizado` | `.env` | Agregarlo a `ALLOWED_DEVICE_IDS` |
| Edge 400 `level fuera de rango` | Firmware/`.env` | Litros negativos o > `MAX_LEVEL` → calibración |
| Edge 400 `capturedAt en el futuro` | Firmware | NTP sin sincronizar o hora local en vez de UTC |
| Edge 401 | Firmware | `X-Edge-Key` distinta de `EDGE_API_KEY` |
| `quality: QUARANTINED` | Backend | (1) deviceId/channel no exactos; (2) token malo o rotado → reemitir; (3) nivel > capacidad; (4) tanque sin vínculo activo |
| Cola que crece, `lastError` con timeout | `/health` | Render dormido o sin internet; la Edge reintenta sola (2 s → 60 s) |
| Lectura `duplicate: true` | Backend | `sequence` repetida → usar la hora UNIX |
| El nivel no cambia en la web aunque esté ACCEPTED | Backend | `capturedAt` igual o anterior a la última lectura aplicada |

Comandos útiles: `docker compose logs -f edge`, `docker compose logs mosquitto`, `curl localhost:8090/health`, `curl localhost:8090/api/v1/readings/recent`.

---

## 10. Pendientes y decisiones abiertas

- [ ] **Prueba de punta a punta con Render y el token real** (nunca se hizo; solo con un backend falso).
- [ ] Revisar el firmware de Felipe contra §5 y **decidir cuál queda en el repo** (el suyo o `firmware/fulltank_sensor`).
- [ ] Alinear los LEDs con el informe (3 LEDs en 25/26/27) si da tiempo.
- [ ] Calibrar las medidas del tanque y poner el divisor de ECHO en la placa real.
- [ ] Confirmar en el enunciado cómo define la "Edge API" (lo hecho cubre HTTP y MQTT).
- [ ] **Informe** (repo `16518-IoT-PrimeFuel/Report-IoT`):
  - capítulo 4: el contenedor *Tank Monitoring Device* todavía dice que el ESP32 envía a `/api/telemetry/readings` con `X-Device-Token` → ahora envía a la **Edge API** y el token vive en la Edge. Agregar los contenedores Edge API y Broker (4.1.3.3) y los nodos de despliegue (4.1.3.4).
  - Figura 5.15 y 5.6: contrato MQTT/HTTP de la Edge.
  - Corregir 4.2.2, 5.6 y T03: el endpoint para emitir el token **ya existe**.
  - "Solution Software Design": justificar el broker público (prototipo/Wokwi) y Mosquitto como plan B; limitaciones (cualquiera puede leer o publicar en el topic, sin garantía de disponibilidad); mitigaciones (sufijo aleatorio, `ALLOWED_DEVICE_IDS`, validación de rango, token fuera del broker). Texto base en `edge/README.md`.

---

## 11. Git

- Rama: `feat/edge-broker`, creada desde `main` (9dfc819) y **ya subida a GitHub** (`16518-IoT-PrimeFuel/backend`):
  - `7d07b64` feat(edge): add Edge API, Mosquitto fallback and ESP32 level sensor firmware
  - `6636a58` feat(edge): enhance sensor configuration and add comprehensive setup guide
  - Luego, este handoff (`docs/HANDOFF-edge-esp32.md`), si Samuel aprobó su commit.
- En la laptop: `git fetch origin && git switch feat/edge-broker && git pull`.
- Todavía no hay PR a `main`. No abrirlo sin el visto bueno.
- `edge/.env` y `firmware/fulltank_sensor/secrets.h` están en `.gitignore`: **nunca deben subirse**.
