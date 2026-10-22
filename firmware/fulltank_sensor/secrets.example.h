// Copiar a secrets.h (no se sube a git) para la placa real. Solo redefine lo que cambia.
#undef WIFI_SSID
#define WIFI_SSID "mi-red"
#undef WIFI_PASSWORD
#define WIFI_PASSWORD "mi-clave"
// deviceId y channel EXACTOS del registro del tanque (si no, CUARENTENA):
#undef DEVICE_ID
#define DEVICE_ID "el-deviceId-del-tanque"
#undef CHANNEL
#define CHANNEL "el-channel-del-tanque"
#undef TOPIC_SUFFIX
#define TOPIC_SUFFIX "el-mismo-sufijo-que-en-la-edge"

// Plan B (Mosquitto en tu PC):
// #undef MQTT_HOST
// #define MQTT_HOST "192.168.1.50"
// #undef MQTT_USER
// #define MQTT_USER "fulltank"
// #undef MQTT_PASS
// #define MQTT_PASS "clave-de-mosquitto"

// HTTP directo a la Edge:
// #undef USE_MQTT
// #define USE_MQTT 0
// #undef EDGE_URL
// #define EDGE_URL "http://192.168.1.50:8090/api/v1/readings"
// #undef EDGE_API_KEY
// #define EDGE_API_KEY "la-misma-EDGE_API_KEY-del-.env"
