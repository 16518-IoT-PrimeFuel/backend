package com.primefuel.fulltank.platform.safety.api.events;

/**
 * Minimal JSON rendering for the safety event payloads, mirroring the tracking/delivery events. The
 * publication registry stores an opaque payload string produced by the owning module (T19-A).
 */
final class SafetyEventJson {

    private SafetyEventJson() {
    }

    static String string(Object value) {
        if (value == null) {
            return "null";
        }
        var raw = String.valueOf(value);
        var escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    static String number(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }
}
