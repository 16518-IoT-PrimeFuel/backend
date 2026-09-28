package com.primefuel.fulltank.platform.equipment.api;

import java.time.Instant;
import java.util.Optional;

/**
 * Machine-facing seam: verifies a device channel credential and, when valid, resolves the tank that was
 * bound at the reading's instant. The raw token is never returned; callers get a decision plus the
 * resolved tank, or an empty response when the reading must be quarantined.
 */
public interface DeviceAuthentication {

    Decision authenticate(String deviceId, String channel, String token, Instant instant);

    record Decision(String outcome, Long tankId, Long organizationId) {

        public boolean authenticated() {
            return "AUTHENTICATED".equals(outcome);
        }

        public Optional<Long> resolvedTankId() {
            return Optional.ofNullable(tankId);
        }

        public static Decision rejected(String outcome) {
            return new Decision(outcome, null, null);
        }
    }
}
