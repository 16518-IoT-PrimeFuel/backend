# Firmware del sensor de nivel (ESP32)

`fulltank_sensor/` mide con el HC-SR04 (JSN-SR04T en la placa real), calcula litros y envía la lectura a la
**Edge API**: por MQTT (broker público o Mosquitto) o por HTTP directo. El dispositivo no tiene el token del backend.

## Wokwi (navegador)

1. Ir a <https://wokwi.com/projects/new/esp32>.
2. Pegar `fulltank_sensor.ino` en `sketch.ino` y reemplazar `diagram.json`.
3. Agregar la biblioteca **PubSubClient** (pestaña *Library Manager*, o un archivo `libraries.txt` con `PubSubClient`).
4. Cambiar `TOPIC_SUFFIX` por el mismo sufijo de `MQTT_TOPIC` en el `.env` de la Edge.
5. ▶ Simular. Mover el control de distancia del HC-SR04: un cambio ≥ 2 % envía al instante; si no, cada 5 min.

Wokwi solo llega a internet (no a tu `localhost`), por eso usa el broker público: la Edge en tu PC se suscribe a él.

## Placa real

Copiar `secrets.example.h` a `secrets.h` (no se sube a git) y completar WiFi y sufijo. Para el plan B o HTTP directo,
descomentar el bloque correspondiente. Compilar con Arduino IDE (placa *ESP32 Dev Module*) + biblioteca PubSubClient.

⚠ El ECHO del sensor trabaja a 5 V: en la placa real usar un divisor (p. ej. 1 kΩ / 2 kΩ) hacia el GPIO 18.

## Datos

| Campo | Valor |
|---|---|
| Pines | TRIG 5, ECHO 18, LED de error 25 |
| `sequence` | Hora UNIX de la captura: siempre crece y sobrevive a reinicios (Wokwi borra la NVS en cada simulación) |
| `capturedAt` | UTC por NTP; no se envía nada sin hora válida |
| Nivel | `nivel_cm = (SENSOR_GAP_CM + USABLE_HEIGHT_CM) − distancia`, % limitado a 0–100, `litros = % × CAPACITY_L / 100` |
| Errores | Sin eco (< 3 de 5 mediciones): no envía y enciende el LED. 400/401 de la Edge: descarta y enciende el LED |

Los parámetros del tanque (25 cm, 100 cm, 1000 L) son de demo: calibrarlos con el tanque real.
