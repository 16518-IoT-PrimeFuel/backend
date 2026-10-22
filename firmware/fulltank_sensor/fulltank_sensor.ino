// FullTank: sensor de nivel del tanque (ESP32 + HC-SR04 / JSN-SR04T).
// Mide, calcula litros y envía la lectura a la Edge API:
//   USE_MQTT 1 -> publica en el broker (público o Mosquitto); la Edge se suscribe.
//   USE_MQTT 0 -> POST HTTP directo a la Edge (misma red).
// El dispositivo NO tiene el token del backend: ese vive solo en la Edge.

#include <WiFi.h>
#include <PubSubClient.h>
#include <HTTPClient.h>
#include <time.h>

// ---------- Configuración (valores para Wokwi; para la placa real, crear secrets.h) ----------
#define USE_MQTT 1
#define WIFI_SSID "Wokwi-GUEST"
#define WIFI_PASSWORD ""
#define MQTT_HOST "broker.hivemq.com"
#define MQTT_PORT 1883
#define MQTT_USER ""            // vacío en el broker público; usuario de Mosquitto en el plan B
#define MQTT_PASS ""
#define TOPIC_SUFFIX "cambia-este-sufijo"   // igual que en MQTT_TOPIC de la Edge
#define EDGE_URL "http://192.168.1.50:8090/api/v1/readings"  // solo USE_MQTT 0
#define EDGE_API_KEY ""                                       // solo USE_MQTT 0
#define DEVICE_ID "esp32-tank-1"
#define TANK_ID "1"

// secrets.h (no se sube a git) redefine lo que haga falta para la placa real.
#if __has_include("secrets.h")
#include "secrets.h"
#endif

// ---------- Pines (DevKit V1) ----------
const int PIN_TRIG = 5;
const int PIN_ECHO = 18;
const int PIN_LED_ERROR = 25;

// ---------- Tanque (valores de demo: calibrar con el tanque real) ----------
const float SENSOR_GAP_CM = 25.0;     // del sensor a la superficie con el tanque lleno
const float USABLE_HEIGHT_CM = 100.0; // altura útil del líquido
const float CAPACITY_L = 1000.0;
const float CHANGE_TRIGGER_PCT = 2.0; // enviar antes si el nivel cambia al menos esto
const unsigned long SAMPLE_EVERY_MS = 10UL * 1000;
const unsigned long PUBLISH_EVERY_MS = 5UL * 60 * 1000;
const unsigned long RETRY_EVERY_MS = 15UL * 1000;

WiFiClient net;
PubSubClient mqtt(net);

struct Reading {
  long long sequence;   // = hora UNIX de la captura: siempre crece y sobrevive a reinicios (Wokwi borra la NVS)
  char capturedAt[25];
  float litres;
  float percent;
};

// ponytail: una sola lectura pendiente; si llega otra antes de enviarla, se envía la más reciente.
Reading pending;
bool hasPending = false;
float lastSentPercent = -100;
unsigned long lastSentAt = 0, lastSampleAt = 0, lastAttemptAt = 0;

// ---------- Medición ----------
float readDistanceCm() {
  digitalWrite(PIN_TRIG, LOW);
  delayMicroseconds(2);
  digitalWrite(PIN_TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_TRIG, LOW);
  unsigned long echoUs = pulseIn(PIN_ECHO, HIGH, 30000);  // ~5 m máximo
  return echoUs == 0 ? -1 : echoUs * 0.0343 / 2.0;
}

// Mediana de 5 mediciones válidas; -1 si hay menos de 3 ecos.
float medianDistanceCm() {
  float v[5];
  int n = 0;
  for (int i = 0; i < 5; i++) {
    float d = readDistanceCm();
    if (d > 0) v[n++] = d;
    delay(60);  // deja morir el eco anterior
  }
  if (n < 3) return -1;
  for (int i = 1; i < n; i++)
    for (int j = i; j > 0 && v[j - 1] > v[j]; j--) { float t = v[j]; v[j] = v[j - 1]; v[j - 1] = t; }
  return v[n / 2];
}

bool measure(Reading &r) {
  float distance = medianDistanceCm();
  if (distance < 0) return false;
  float levelCm = (SENSOR_GAP_CM + USABLE_HEIGHT_CM) - distance;
  r.percent = constrain(levelCm / USABLE_HEIGHT_CM * 100.0, 0.0, 100.0);
  r.litres = r.percent * CAPACITY_L / 100.0;
  time_t now = time(nullptr);
  r.sequence = (long long) now;
  strftime(r.capturedAt, sizeof r.capturedAt, "%Y-%m-%dT%H:%M:%SZ", gmtime(&now));
  Serial.printf("Distancia %.1f cm -> %.1f %% -> %.1f L\n", distance, r.percent, r.litres);
  return true;
}

// ---------- Envío ----------
void toJson(const Reading &r, char *out, size_t size) {
  snprintf(out, size,
           "{\"schemaVersion\":1,\"deviceId\":\"%s\",\"channel\":\"level\",\"sequence\":%lld,"
           "\"capturedAt\":\"%s\",\"level\":%.1f,\"unit\":\"LITRE\"}",
           DEVICE_ID, r.sequence, r.capturedAt, r.litres);
}

// true = la lectura quedó entregada o debe descartarse; false = reintentar.
bool sendMqtt(const char *json) {
  if (!mqtt.connected()) {
    String clientId = String(DEVICE_ID) + "-" + String((uint32_t) esp_random(), HEX);
    bool ok = strlen(MQTT_USER) ? mqtt.connect(clientId.c_str(), MQTT_USER, MQTT_PASS)
                                : mqtt.connect(clientId.c_str());
    if (!ok) {
      Serial.printf("MQTT: no conecta (estado %d)\n", mqtt.state());
      return false;
    }
  }
  String topic = String("fulltank/") + TOPIC_SUFFIX + "/tanks/" + TANK_ID + "/readings";
  bool ok = mqtt.publish(topic.c_str(), json);
  Serial.printf("MQTT %s -> %s\n", topic.c_str(), ok ? "publicado" : "falló");
  return ok;
}

bool sendHttp(const char *json) {
  HTTPClient http;
  http.begin(EDGE_URL);
  http.setTimeout(10000);
  http.addHeader("Content-Type", "application/json");
  if (strlen(EDGE_API_KEY)) http.addHeader("X-Edge-Key", EDGE_API_KEY);
  int status = http.POST((uint8_t *) json, strlen(json));
  Serial.printf("HTTP Edge -> %d %s\n", status, http.getString().c_str());
  http.end();
  if (status == 202) return true;
  if (status == 400 || status == 401) {  // reenviar no cambiaría nada: descartar y avisar
    digitalWrite(PIN_LED_ERROR, HIGH);
    return true;
  }
  return false;  // sin respuesta o 5xx: reintentar la misma lectura (misma sequence)
}

void trySend() {
  if (!hasPending || WiFi.status() != WL_CONNECTED) return;
  char json[256];
  toJson(pending, json, sizeof json);
  lastAttemptAt = millis();
  bool done = USE_MQTT ? sendMqtt(json) : sendHttp(json);
  if (done) {
    hasPending = false;
    lastSentPercent = pending.percent;
    lastSentAt = millis();
  }
}

// ---------- Ciclo ----------
void setup() {
  Serial.begin(115200);
  pinMode(PIN_TRIG, OUTPUT);
  pinMode(PIN_ECHO, INPUT);
  pinMode(PIN_LED_ERROR, OUTPUT);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("WiFi");
  while (WiFi.status() != WL_CONNECTED) { delay(250); Serial.print("."); }
  Serial.printf(" OK %s\n", WiFi.localIP().toString().c_str());

  // Sin hora válida no hay capturedAt ni sequence: esperar a NTP.
  configTime(0, 0, "pool.ntp.org", "time.google.com");
  Serial.print("NTP");
  while (time(nullptr) < 1700000000) { delay(250); Serial.print("."); }
  Serial.println(" OK");

  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setBufferSize(512);  // el JSON supera los 256 bytes por defecto con margen
}

void loop() {
  if (USE_MQTT) mqtt.loop();
  if (WiFi.status() != WL_CONNECTED) WiFi.reconnect();

  if (millis() - lastSampleAt >= SAMPLE_EVERY_MS || lastSampleAt == 0) {
    lastSampleAt = millis();
    Reading r;
    if (!measure(r)) {
      Serial.println("Sin eco: no se envía");
      digitalWrite(PIN_LED_ERROR, HIGH);
    } else {
      digitalWrite(PIN_LED_ERROR, LOW);
      bool changed = fabs(r.percent - lastSentPercent) >= CHANGE_TRIGGER_PCT;
      bool due = lastSentAt == 0 || millis() - lastSentAt >= PUBLISH_EVERY_MS;
      if (changed || due) {
        pending = r;
        hasPending = true;
        trySend();
      }
    }
  }

  if (hasPending && millis() - lastAttemptAt >= RETRY_EVERY_MS) trySend();
  delay(50);
}
