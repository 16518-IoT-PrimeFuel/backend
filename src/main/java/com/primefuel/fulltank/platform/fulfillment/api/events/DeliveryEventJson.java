package com.primefuel.fulltank.platform.fulfillment.api.events;

/**
 * Minimal JSON rendering for the delivery event payloads. The publication registry stores an opaque
 * payload string produced by the owning module (T19-A); this keeps that production in one place without
 * pulling a serializer into the contract.
 */
final class DeliveryEventJson {

    private DeliveryEventJson() {
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
